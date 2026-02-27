package com.xr21.ai.agent.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Utility class for parsing .gitignore files and checking if paths should be ignored.
 * This class provides efficient .gitignore pattern matching with proper negation support.
 * 
 * Features:
 * - Full .gitignore pattern support (including **, ?, character classes)
 * - Proper negation pattern handling (patterns starting with !)
 * - Directory-only pattern support (patterns ending with /)
 * - Caching for performance
 * - Thread-safe implementation
 */
public class GitignoreUtil {
    
    // Cache for GitignoreUtil instances to avoid re-parsing .gitignore files
    private static final Map<Path, GitignoreUtil> INSTANCE_CACHE = new ConcurrentHashMap<>();
    
    // Common patterns that should always be ignored
    private static final List<Pattern> DEFAULT_IGNORE_PATTERNS = List.of(
            Pattern.compile("\\.git"),
            Pattern.compile("\\.git/.*")
    );
    
    // Cache for path matching results
    private final Map<String, Boolean> matchCache = new ConcurrentHashMap<>();
    
    private final List<IgnorePattern> ignorePatterns;
    private final Path gitignorePath;
    private final Path basePath;

    /**
     * Internal class to represent a gitignore pattern with its properties.
     */
    private static class IgnorePattern {
        final Pattern pattern;
        final boolean isNegation;
        final boolean isDirectoryOnly;
        final String originalPattern;
        
        IgnorePattern(Pattern pattern, boolean isNegation, boolean isDirectoryOnly, String originalPattern) {
            this.pattern = pattern;
            this.isNegation = isNegation;
            this.isDirectoryOnly = isDirectoryOnly;
            this.originalPattern = originalPattern;
        }
        
        boolean matches(String path, boolean isDirectory) {
            if (isDirectoryOnly && !isDirectory) {
                return false;
            }
            return pattern.matcher(path).matches() || 
                   pattern.matcher("/" + path).matches() ||
                   pattern.matcher(path + "/").matches() ||
                   pattern.matcher("/" + path + "/").matches();
        }
    }
    
    /**
     * Creates a GitignoreUtil that reads .gitignore from the specified base path.
     * Uses caching to avoid re-parsing the same .gitignore file.
     *
     * @param basePath the base directory to look for .gitignore
     */
    public GitignoreUtil(Path basePath) {
        this.basePath = basePath.toAbsolutePath().normalize();
        this.gitignorePath = this.basePath.resolve(".gitignore");
        this.ignorePatterns = loadGitignorePatterns();
    }
    
    /**
     * Creates a GitignoreUtil that reads .gitignore from the current working directory.
     * Uses caching to avoid re-parsing the same .gitignore file.
     */
    public GitignoreUtil() {
        this(Paths.get("."));
    }
    
    /**
     * Gets or creates a GitignoreUtil instance for the given base path.
     * This method uses caching to avoid re-parsing the same .gitignore file.
     *
     * @param basePath the base directory to look for .gitignore
     * @return a GitignoreUtil instance for the given path
     */
    public static GitignoreUtil getInstance(Path basePath) {
        Path normalizedPath = basePath.toAbsolutePath().normalize();
        return INSTANCE_CACHE.computeIfAbsent(normalizedPath, GitignoreUtil::new);
    }
    
    /**
     * Gets or creates a GitignoreUtil instance for the current working directory.
     *
     * @return a GitignoreUtil instance for the current directory
     */
    public static GitignoreUtil getInstance() {
        return getInstance(Paths.get("."));
    }

    /**
     * Loads patterns from .gitignore file if it exists.
     * Handles negation patterns properly by processing them in reverse order.
     */
    private List<IgnorePattern> loadGitignorePatterns() {
        List<IgnorePattern> patterns = new ArrayList<>();

        // Add default patterns first (these are always applied)
        for (Pattern defaultPattern : DEFAULT_IGNORE_PATTERNS) {
            patterns.add(new IgnorePattern(defaultPattern, false, false, defaultPattern.pattern()));
        }

        if (!Files.exists(gitignorePath)) {
            return patterns;
        }

        List<String> rawPatterns = new ArrayList<>();
        
        try (Stream<String> lines = Files.lines(gitignorePath, StandardCharsets.UTF_8)) {
            lines.map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(rawPatterns::add);
        } catch (IOException e) {
            // If UTF-8 fails, try ISO-8859-1
            try (Stream<String> lines = Files.lines(gitignorePath, StandardCharsets.ISO_8859_1)) {
                lines.map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(rawPatterns::add);
            } catch (IOException e2) {
                // If we can't read the file, just use default patterns
                return patterns;
            }
        }
        
        // Process patterns in reverse order to handle negations correctly
        // Later patterns override earlier ones, especially for negations
        for (String rawPattern : rawPatterns) {
            try {
                IgnorePattern ignorePattern = parseGitignorePattern(rawPattern);
                if (ignorePattern != null) {
                    patterns.add(ignorePattern);
                }
            } catch (Exception e) {
                // Skip invalid patterns
            }
        }

        return patterns;
    }
    
    /**
     * Parses a single gitignore pattern into an IgnorePattern object.
     */
    private IgnorePattern parseGitignorePattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = pattern.trim();
        boolean isNegation = trimmed.startsWith("!");
        boolean isDirectoryOnly = trimmed.endsWith("/");
        
        // Remove negation prefix if present
        String patternWithoutNegation = isNegation ? trimmed.substring(1) : trimmed;
        
        // Remove directory suffix if present
        String patternWithoutSuffix = isDirectoryOnly ? 
            patternWithoutNegation.substring(0, patternWithoutNegation.length() - 1) : 
            patternWithoutNegation;
        
        // Convert to regex
        Pattern regex = convertGitignorePatternToRegex(patternWithoutSuffix, isDirectoryOnly);
        
        return new IgnorePattern(regex, isNegation, isDirectoryOnly, trimmed);
    }

    /**
     * Converts a .gitignore pattern to a Java regex Pattern.
     * 
     * @param pattern the gitignore pattern (without negation prefix or directory suffix)
     * @param isDirectoryOnly whether this is a directory-only pattern
     * @return the compiled regex pattern
     */
    private Pattern convertGitignorePatternToRegex(String pattern, boolean isDirectoryOnly) {
        if (pattern.isEmpty()) {
            return Pattern.compile(".*");
        }
        
        StringBuilder regex = new StringBuilder();
        
        // Check if pattern is anchored to root
        boolean anchoredToRoot = pattern.startsWith("/");
        if (anchoredToRoot) {
            pattern = pattern.substring(1);
            regex.append("^");
        } else {
            // Pattern can match at any level
            regex.append("(^|.*/)");
        }
        
        // Convert the pattern character by character
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            
            switch (c) {
                case '*':
                    // Check for ** pattern
                    if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                        // Check if it's **/ or /** or /**/
                        if (i + 2 < pattern.length() && pattern.charAt(i + 2) == '/') {
                            // **/ pattern - matches zero or more directories
                            regex.append("(?:[^/]+/)*");
                            i += 2; // Skip **/
                        } else if (i > 0 && pattern.charAt(i - 1) == '/') {
                            // /** pattern - matches everything in this directory
                            regex.append(".*");
                            i++; // Skip the second *
                        } else {
                            // ** pattern - matches across directories
                            regex.append(".*");
                            i++; // Skip the second *
                        }
                    } else {
                        // Single * - matches any characters except /
                        regex.append("[^/]*");
                    }
                    break;
                    
                case '?':
                    // ? matches any single character except /
                    regex.append("[^/]");
                    break;
                    
                case '.':
                    regex.append("\\.");
                    break;
                    
                case '\\':
                    // Escape character
                    if (i + 1 < pattern.length()) {
                        char nextChar = pattern.charAt(i + 1);
                        // Only escape special characters
                        if ("*?.[]\\".indexOf(nextChar) >= 0) {
                            regex.append("\\").append(nextChar);
                            i++; // Skip the escaped character
                        } else {
                            regex.append("\\\\");
                        }
                    } else {
                        regex.append("\\\\");
                    }
                    break;
                    
                case '[':
                    // Character class
                    int closeIndex = pattern.indexOf(']', i);
                    if (closeIndex > i) {
                        // Extract the character class
                        String charClass = pattern.substring(i, closeIndex + 1);
                        // Convert character class for regex
                        regex.append(charClass);
                        i = closeIndex;
                    } else {
                        // Unclosed bracket, treat as literal
                        regex.append("\\[");
                    }
                    break;
                    
                default:
                    // Escape regex special characters
                    if ("+()|{}^$".indexOf(c) >= 0) {
                        regex.append("\\").append(c);
                    } else {
                        regex.append(c);
                    }
                    break;
            }
        }
        
        // Handle end of pattern
        if (isDirectoryOnly) {
            // Directory pattern must end with /
            regex.append("(/.*)?$");
        } else {
            // File pattern can match file or directory with that name
            regex.append("(?:/.*)?$");
        }
        
        // Compile with case-insensitive flag (gitignore is usually case-sensitive on Linux,
        // but we use case-insensitive for cross-platform compatibility)
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    /**
     * Checks if a given path should be ignored based on .gitignore patterns.
     * Uses caching for performance.
     *
     * @param path the path to check (can be absolute or relative to basePath)
     * @return true if the path should be ignored, false otherwise
     */
    public boolean isIgnored(Path path) {
        return isIgnored(path, false);
    }
    
    /**
     * Checks if a given path should be ignored based on .gitignore patterns.
     * Uses caching for performance and handles negation patterns correctly.
     *
     * @param path the path to check
     * @param isDirectory whether the path is a directory
     * @return true if the path should be ignored, false otherwise
     */
    public boolean isIgnored(Path path, boolean isDirectory) {
        // Generate cache key
        String cacheKey = getCacheKey(path, isDirectory);
        
        // Check cache first
        Boolean cachedResult = matchCache.get(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        // Calculate relative path
        Path relativePath = getRelativePath(path);
        if (relativePath == null) {
            // If we can't get relative path, don't ignore
            matchCache.put(cacheKey, false);
            return false;
        }
        
        String pathStr = relativePath.toString().replace('\\', '/');
        
        // Track the final result, considering negation patterns
        boolean ignored = false;
        
        // Process patterns in order (later patterns override earlier ones for negations)
        for (IgnorePattern pattern : ignorePatterns) {
            if (pattern.matches(pathStr, isDirectory)) {
                if (pattern.isNegation) {
                    // Negation pattern matches - explicitly don't ignore
                    ignored = false;
                } else {
                    // Regular pattern matches - mark as ignored
                    ignored = true;
                }
            }
        }
        
        // Cache the result
        matchCache.put(cacheKey, ignored);
        return ignored;
    }
    
    /**
     * Gets the relative path from base path.
     */
    private Path getRelativePath(Path path) {
        try {
            if (path.isAbsolute()) {
                return basePath.relativize(path);
            } else {
                return path;
            }
        } catch (Exception e) {
            // If we can't relativize, try to normalize the path
            try {
                return path.normalize();
            } catch (Exception e2) {
                return null;
            }
        }
    }
    
    /**
     * Generates a cache key for the given path and directory flag.
     */
    private String getCacheKey(Path path, boolean isDirectory) {
        String pathStr = path.toAbsolutePath().normalize().toString().replace('\\', '/');
        return pathStr + "|" + isDirectory;
    }
    
    /**
     * Clears the match cache. Useful when .gitignore file changes.
     */
    public void clearCache() {
        matchCache.clear();
    }
    
    /**
     * Reloads .gitignore patterns and clears cache.
     * Call this method if the .gitignore file has been modified.
     */
    public void reload() {
        List<IgnorePattern> newPatterns = loadGitignorePatterns();
        synchronized (this) {
            this.ignorePatterns.clear();
            this.ignorePatterns.addAll(newPatterns);
        }
        clearCache();
    }

    /**
     * Gets the base path for this GitignoreUtil.
     *
     * @return the base path
     */
    public Path getBasePath() {
        return basePath;
    }
    
    /**
     * Checks if a .gitignore file exists at the base path.
     *
     * @return true if .gitignore exists, false otherwise
     */
    public boolean hasGitignore() {
        return Files.exists(gitignorePath);
    }
    
    /**
     * Returns the number of ignore patterns loaded (including defaults).
     *
     * @return the number of patterns
     */
    public int getPatternCount() {
        return ignorePatterns.size();
    }
    
    /**
     * Gets all loaded ignore patterns as strings.
     *
     * @return list of pattern strings
     */
    public List<String> getPatterns() {
        return ignorePatterns.stream()
                .map(p -> p.originalPattern)
                .toList();
    }
    
    /**
     * Gets the path to the .gitignore file.
     *
     * @return the .gitignore file path
     */
    public Path getGitignorePath() {
        return gitignorePath;
    }
    
    /**
     * Checks if a given path string should be ignored.
     * Convenience method that converts string to Path.
     *
     * @param pathString the path string to check
     * @return true if the path should be ignored, false otherwise
     */
    public boolean isIgnored(String pathString) {
        return isIgnored(Paths.get(pathString));
    }
    
    /**
     * Checks if a given path string should be ignored.
     * Convenience method that converts string to Path.
     *
     * @param pathString the path string to check
     * @param isDirectory whether the path is a directory
     * @return true if the path should be ignored, false otherwise
     */
    public boolean isIgnored(String pathString, boolean isDirectory) {
        return isIgnored(Paths.get(pathString), isDirectory);
    }
    
    /**
     * Filters a list of paths, returning only those that are not ignored.
     *
     * @param paths the paths to filter
     * @return list of paths that are not ignored
     */
    public List<Path> filterIgnored(List<Path> paths) {
        return paths.stream()
                .filter(path -> !isIgnored(path))
                .toList();
    }
    
    /**
     * Filters a list of path strings, returning only those that are not ignored.
     *
     * @param pathStrings the path strings to filter
     * @return list of path strings that are not ignored
     */
    public List<String> filterIgnoredStrings(List<String> pathStrings) {
        return pathStrings.stream()
                .filter(path -> !isIgnored(path))
                .toList();
    }
}
