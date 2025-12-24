import os
import subprocess
import re
import sys

# Configuration
PATCHES_DIR = "/home/x/defects4j_test/arja_patches_Math_2"
PROJECT_DIR = "/home/x/defects4j_test/Math_2"
LOG_FILE = "patch_verification_math2_results.csv"

def log(message):
    print(f"[BatchVerifier] {message}")
    with open("batch_verify.log", "a") as f:
        f.write(f"[BatchVerifier] {message}\n")

def run_command(command, cwd=None, ignore_errors=False):
    try:
        result = subprocess.run(
            command, 
            cwd=cwd, 
            shell=True, 
            check=True, 
            stdout=subprocess.PIPE, 
            stderr=subprocess.PIPE,
            universal_newlines=True
        )
        return result.stdout
    except subprocess.CalledProcessError as e:
        if not ignore_errors:
            # log(f"Command failed: {command}\nError: {e.stderr}")
            pass
        return e.stdout + e.stderr

def reset_project():
    if os.path.exists(os.path.join(PROJECT_DIR, ".git")):
        run_command("git checkout .", cwd=PROJECT_DIR)
        run_command("git clean -fd", cwd=PROJECT_DIR)
    else:
        run_command("defects4j checkout -p Math -v 1b -w .", cwd=PROJECT_DIR)

def get_triggering_tests():
    log("Identifying triggering tests...")
    # Ensure project is in buggy state or just use export which reads metadata
    output = run_command("defects4j export -p tests.trigger", cwd=PROJECT_DIR)
    tests = [t.strip() for t in output.splitlines() if t.strip()]
    log(f"Found {len(tests)} triggering tests: {tests}")
    return tests

def verify_patch(patch_name, patch_path, triggering_tests):
    log(f"Testing {patch_name}...")
    
    # 1. Reset Project
    reset_project()
    
    # 2. Apply Patch
    # Calculate strip level for absolute paths
    # e.g. /home/x/defects4j_test/Math_1b/src/... -> strip 5 levels to get src/...
    # PROJECT_DIR should be absolute
    abs_project_dir = os.path.abspath(PROJECT_DIR)
    # We need to strip the project dir path.
    # /home/x/defects4j_test/Math_1b has 4 slashes, but we need to strip 5 slashes to get relative path
    strip_level = abs_project_dir.count(os.sep) + 1
    
    # Try multiple strip levels
    strategies = [
        f"patch -p{strip_level} < {patch_path}", # Try stripping to project root (Most likely to work)
        f"patch -p0 < {patch_path}", # Try absolute path
        f"patch -p1 < {patch_path}" # Try standard p1
    ]
    
    applied = False
    for cmd in strategies:
        # log(f"  Trying: {cmd}")
        output = run_command(cmd, cwd=PROJECT_DIR, ignore_errors=True)
        if "reject" not in output and "fail" not in output.lower() and "can't find file" not in output.lower():
            applied = True
            break
        else:
            reset_project() # Reset before next try
            
    if not applied:
        return "Apply Failed"

    # 3. Compile
    compile_out = run_command("defects4j compile", cwd=PROJECT_DIR, ignore_errors=True)
    if "FAIL" in compile_out:
        return "Compile Failed"

    # 4. Fast Validation (Triggering Tests)
    if triggering_tests:
        # log(f"  Running fast validation ({len(triggering_tests)} tests)...")
        for test in triggering_tests:
            # defects4j test -t class::method
            cmd = f"defects4j test -t {test}"
            out = run_command(cmd, cwd=PROJECT_DIR, ignore_errors=True)
            if "Failing tests: 0" not in out:
                 return f"Failed (Triggering)"
    
    # 5. Full Validation
    log("  Triggering tests passed. Running full regression test...")
    test_out = run_command("defects4j test", cwd=PROJECT_DIR, ignore_errors=True)
    
    # 6. Parse Result
    match = re.search(r"Failing tests:\s*(\d+)", test_out)
    if match:
        failing_count = int(match.group(1))
        if failing_count == 0:
            return "Success"
        else:
            # Extract failing test names
            failing_tests = []
            lines = test_out.splitlines()
            capture = False
            for line in lines:
                if "Failing tests:" in line:
                    capture = True
                    continue
                if capture:
                    if line.strip().startswith("-"):
                        failing_tests.append(line.strip().replace("- ", ""))
                    elif line.strip() == "":
                        continue
                    else:
                        break
            
            tests_str = ", ".join(failing_tests[:3])
            if len(failing_tests) > 3:
                tests_str += "..."
            return f"Failed ({failing_count} tests: {tests_str})"
    else:
        return "Test Execution Failed"

def main():
    if not os.path.exists(PATCHES_DIR):
        log(f"Error: Patches directory not found: {PATCHES_DIR}")
        return

    if not os.path.exists(PROJECT_DIR):
        log(f"Error: Project directory not found: {PROJECT_DIR}")
        return

    # Get triggering tests once
    triggering_tests = get_triggering_tests()

    results = []
    
    # Get all patch directories
    patch_dirs = [d for d in os.listdir(PATCHES_DIR) if os.path.isdir(os.path.join(PATCHES_DIR, d))]
    
    # Natural sort for Patch_1, Patch_2, Patch_10
    patch_dirs.sort(key=lambda x: int(x.split('_')[1]) if '_' in x and x.split('_')[1].isdigit() else x)

    log(f"Found {len(patch_dirs)} patches to verify.")
    
    print(f"{'Patch':<20} | {'Status':<30}")
    print("-" * 55)

    # Initialize CSV with header
    with open(LOG_FILE, "w") as f:
        f.write("Patch,Status\n")

    for patch_dir in patch_dirs:
        try:
            full_patch_dir = os.path.join(PATCHES_DIR, patch_dir)
            diff_file = os.path.join(full_patch_dir, "diff")
            
            if not os.path.exists(diff_file):
                files = os.listdir(full_patch_dir)
                diff_file = None
                for f in files:
                    if f == "diff" or f.endswith(".patch") or f.endswith(".diff"):
                        diff_file = os.path.join(full_patch_dir, f)
                        break
                
            if not diff_file:
                status = "No diff file found"
            else:
                status = verify_patch(patch_dir, diff_file, triggering_tests)
        except Exception as e:
            status = f"Error: {str(e)}"
            log(f"Error verifying {patch_dir}: {e}")
            
        print(f"{patch_dir:<20} | {status:<30}")
        
        # Append result immediately
        with open(LOG_FILE, "a") as f:
            f.write(f"{patch_dir},{status}\n")
    
    log(f"Verification complete. Results saved to {LOG_FILE}")

if __name__ == "__main__":
    main()
