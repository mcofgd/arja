package us.msu.cse.repair.core.util;

import java.net.URLClassLoader;

/**
 * Utility class for safely loading classes, handling java.lang classes and other special cases
 */
public class SafeClassLoader {
    
    /**
     * Safely load a class, handling java.lang classes and other special cases
     * 
     * @param className The class name to load
     * @param classLoader The custom classLoader to try first
     * @return The loaded Class object
     * @throws ClassNotFoundException if the class cannot be found
     */
    public static Class<?> loadClass(String className, URLClassLoader classLoader) 
            throws ClassNotFoundException {
        // 1. First, try to load with the custom classLoader
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            // 2. If failed, try with system classLoader (for java.lang.* classes)
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e2) {
                // 3. If still failed, try adding java.lang prefix for common classes
                if (!className.contains(".")) {
                    try {
                        return Class.forName("java.lang." + className);
                    } catch (ClassNotFoundException e3) {
                        // 4. Try java.util prefix
                        try {
                            return Class.forName("java.util." + className);
                        } catch (ClassNotFoundException e4) {
                            // 5. Try java.math prefix
                            try {
                                return Class.forName("java.math." + className);
                            } catch (ClassNotFoundException e5) {
                                // Give up and throw the original exception
                                throw e;
                            }
                        }
                    }
                }
                throw e;
            }
        }
    }
    
    /**
     * Safely load a class, returning null if it cannot be loaded
     */
    public static Class<?> loadClassSilently(String className, URLClassLoader classLoader) {
        try {
            return loadClass(className, classLoader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}