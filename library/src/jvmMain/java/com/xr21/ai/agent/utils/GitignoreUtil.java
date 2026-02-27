package com.xr21.ai.agent.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Utility class for parsing .gitignore files and checking if paths should be ignored.
 * This class dynamically reads .gitignore from the current working directory.
 */
public class GitignoreUtil {

    // Common patterns that should always be ignored
    private static final List<Pattern> DEFAULT_IGNORE_PATTERNS = List.of(
            Pattern.compile("\\.git"),
            Pattern.compile("\\.git/.*")
    );

    private final List<Pattern> ignorePatterns;
    private final Path gitignorePath;
    private final Path basePath;

    /**
     * Creates a GitignoreUtil that reads .gitignore from the specified base path.
     *
     * @param basePath the base directory to look for .gitignore
     */
    public GitignoreUtil(Path basePath) {
        this.basePath = basePath;
        this.gitignorePath = basePath.resolve(".gitignore");
        this.ignorePatterns = loadGitignorePatterns();
    }

    /**
     * Creates a GitignoreUtil that reads .gitignore from the current working directory.
     */
    public GitignoreUtil() {
        this(Paths.get(".").toAbsolutePath().normalize());
    }

    /**
     * Loads patterns from .gitignore file if it exists.
     */
    private List<Pattern> loadGitignorePatterns() {
        List<Pattern> patterns = new ArrayList<>();

        // Add default patterns first
        patterns.addAll(DEFAULT_IGNORE_PATTERNS);

        if (!Files.exists(gitignorePath)) {
            return patterns;
        }

        try (Stream<String> lines = Files.lines(gitignorePath)) {
            lines.map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(pattern -> {
                        try {
                            Pattern regex = convertGitignorePatternToRegex(pattern);
                            patterns.add(regex);
                        } catch (Exception e) {
                            // Skip invalid patterns
                        }
                    });
        } catch (IOException e) {
            // If we can't read the file, just use default patterns
        }

        return patterns;
    }

    /**
     * Converts a .gitignore pattern to a Java regex Pattern.
     */
    private Pattern convertGitignorePatternToRegex(String pattern) {
        StringBuilder regex = new StringBuilder();

        // Handle negation patterns (starting with !)
        boolean isNegation = false;
        if (pattern.startsWith("!")) {
            isNegation = true;
            pattern = pattern.substring(1);
        }

        // Handle directory-only patterns (ending with /)
        boolean isDirectoryOnly = pattern.endsWith("/");
        if (isDirectoryOnly) {
            pattern = pattern.substring(0, pattern.length() - 1);
        }

        // Handle patterns starting with /
        boolean anchoredToRoot = pattern.startsWith("/");
        if (anchoredToRoot) {
            pattern = pattern.substring(1);
            regex.append("^");
        } else {
            // Pattern can match at any level
            regex.append("(^|.*/|/)");
        }

        // Convert the pattern
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            switch (c) {
                case '*':
                    // Check if it's ** (matches across directories)
                    if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                        regex.append(".*");
                        i++; // Skip the next *
                    } else {
                        regex.append("[^/]*"); // * doesn't match /
                    }
                    break;
                case '?':
                    regex.append("[^/]");
                    break;
                case '.':
                    regex.append("\\.");
                    break;
                case '\\':
                    regex.append("\\\\");
                    break;
                case '[':
                    // Handle character classes
                    int closeIndex = pattern.indexOf(']', i);
                    if (closeIndex > i) {
                        regex.append(pattern.substring(i, closeIndex + 1));
                        i = closeIndex;
                    } else {
                        regex.append("\\[");
                    }
                    break;
                default:
                    regex.append(Pattern.quote(String.valueOf(c)));
            }
        }

        // Handle end of pattern
        if (isDirectoryOnly) {
            regex.append("(/.*)?$");
        } else {
            regex.append("($|/.*)");
        }

        // Compile with appropriate flags
        int flags = Pattern.CASE_INSENSITIVE;
        if (isNegation) {
            // For negation patterns, we need special handling
            // This is simplified - full negation support requires more complex logic
            flags |= Pattern.UNICODE_CASE;
        }

        return Pattern.compile(regex.toString(), flags);
    }

    /**
     * Checks if a given path should be ignored based on .gitignore patterns.
     *
     * @param path the path to check (can be absolute or relative to basePath)
     * @return true if the path should be ignored, false otherwise
     */
    public boolean isIgnored(Path path) {
        return isIgnored(path, false);
    }

    /**
     * Checks if a given path should be ignored based on .gitignore patterns.
     *
     * @param path the path to check
     * @param isDirectory whether the path is a directory
     * @return true if the path should be ignored, false otherwise
     */
    public boolean isIgnored(Path path, boolean isDirectory) {
        Path relativePath;
        try {
            if (path.isAbsolute()) {
                relativePath = basePath.relativize(path);
            } else {
                relativePath = path;
            }
        } catch (Exception e) {
            // If we can't relativize, use the path as-is
            relativePath = path;
        }

        String pathStr = relativePath.toString().replace('\\', '/');
        String pathStrWithSlash = pathStr.endsWith("/") ? pathStr : pathStr + "/";
        String fileName = relativePath.getFileName() != null ?
                relativePath.getFileName().toString() : pathStr;

        for (Pattern pattern : ignorePatterns) {
            // Try matching against the full path
            if (pattern.matcher(pathStr).matches() ||
                    pattern.matcher("/" + pathStr).matches() ||
                    pattern.matcher(pathStrWithSlash).matches() ||
                    pattern.matcher("/" + pathStrWithSlash).matches()) {
                return true;
            }

            // Try matching against just the file/directory name
            if (pattern.matcher(fileName).matches()) {
                return true;
            }

            // Check if any parent directory matches
            Path parent = relativePath.getParent();
            while (parent != null) {
                String parentName = parent.getFileName() != null ?
                        parent.getFileName().toString() : "";
                if (pattern.matcher(parentName).matches() ||
                        pattern.matcher(parentName + "/").matches()) {
                    return true;
                }
                parent = parent.getParent();
            }
        }

        return false;
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
}
