package org.fuin.porelo.ecosystem.maven;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The settings file a shell line passes to Maven, read as a shell splits it: commands at {@code ;},
 * {@code &}, {@code |} and parentheses, words at unquoted spaces with quotes and backslashes taken
 * away, what {@code $(…)} and backticks run as commands of their own, and Maven only where it is
 * the command, never an argument of another one.
 */
final class ShellCommand {
    private static final Set<String> MAVEN = Set.of("mvn", "mvnw");
    private static final Set<String> SHELLS = Set.of("sh", "bash", "dash", "zsh");
    // Words after which the next word is still a command's name.
    private static final Set<String> KEYWORDS =
            Set.of("if", "then", "else", "elif", "do", "while", "until", "!", "{");
    // Commands that run the command after their options, with the options that take an argument.
    private static final Map<String, Set<String>> WRAPPERS =
            Map.of(
                    "sudo", Set.of("-u", "-g", "-C", "-h", "-p"),
                    "exec", Set.of("-a"),
                    "time", Set.of("-f", "-o"),
                    "env", Set.of("-u", "-C", "-S"),
                    "command", Set.of(),
                    "nohup", Set.of(),
                    "timeout", Set.of("-s", "-k"),
                    "xvfb-run", Set.of("-s", "-n", "-f", "-e", "-p", "-w"));
    private static final Pattern ASSIGNMENT =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*=.*", Pattern.DOTALL);
    // What a substitution's output stands in for: unknown, and no file name.
    private static final char UNKNOWN = '\0';
    private static final int MAX_DEPTH = 4;

    private ShellCommand() {}

    /**
     * The settings file a line passes to Maven.
     *
     * @param line one logical line of shell, its continuations joined and its comment cut
     * @return the file as written; empty when no command of the line runs Maven with one
     */
    static Optional<String> settingsFile(String line) {
        return settingsFile(line, 0);
    }

    /**
     * A line without its comment: from a {@code #} that starts a word outside quotes.
     *
     * @param line one line
     * @return the line up to its comment
     */
    static String withoutComment(String line) {
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote == '\'') {
                quote = c == '\'' ? 0 : quote;
            } else if (c == '\\') {
                i++;
            } else if (quote == '"') {
                quote = c == '"' ? 0 : quote;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '#' && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static Optional<String> settingsFile(String line, int depth) {
        if (depth > MAX_DEPTH) {
            return Optional.empty();
        }
        Parse parse = new Parse(line);
        for (List<String> command : parse.commands) {
            Optional<String> named = ofCommand(command, depth);
            if (named.isPresent()) {
                return named;
            }
        }
        for (String inner : parse.substitutions) {
            Optional<String> named = settingsFile(inner, depth + 1);
            if (named.isPresent()) {
                return named;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> ofCommand(List<String> words, int depth) {
        int i = 0;
        while (i < words.size()) {
            String word = words.get(i);
            if (ASSIGNMENT.matcher(word).matches() || KEYWORDS.contains(word)) {
                i++;
                continue;
            }
            Set<String> withArgument = WRAPPERS.get(word);
            if (withArgument == null) {
                break;
            }
            i++;
            while (i < words.size() && words.get(i).startsWith("-")) {
                String option = words.get(i++);
                if (option.equals("--")) {
                    break;
                }
                if (withArgument.contains(option)) {
                    i++;
                }
            }
            if (word.equals("timeout")) {
                i++; // the duration
            }
        }
        if (i >= words.size()) {
            return Optional.empty();
        }
        String name = baseName(words.get(i));
        if (SHELLS.contains(name)) {
            return script(words, i + 1).flatMap(s -> settingsFile(s, depth + 1));
        }
        if (!MAVEN.contains(name)) {
            return Optional.empty();
        }
        for (int k = i + 1; k < words.size(); k++) {
            String word = words.get(k);
            if ((word.equals("-s") || word.equals("--settings")) && k + 1 < words.size()) {
                return Optional.of(words.get(k + 1));
            }
            if (word.startsWith("--settings=")) {
                return Optional.of(word.substring("--settings=".length()));
            }
        }
        return Optional.empty();
    }

    // The script a shell is given with -c, alone or among other short options as in -ec.
    private static Optional<String> script(List<String> words, int from) {
        boolean command = false;
        int k = from;
        while (k < words.size() && words.get(k).startsWith("-")) {
            String option = words.get(k++);
            if (option.equals("--")) {
                break;
            }
            if (option.equals("-o")) {
                k++;
            } else if (!option.startsWith("--") && option.indexOf('c') > 0) {
                command = true;
            }
        }
        return command && k < words.size() ? Optional.of(words.get(k)) : Optional.empty();
    }

    private static String baseName(String word) {
        int slash = word.lastIndexOf('/');
        return slash < 0 ? word : word.substring(slash + 1);
    }

    // One pass over a line as a shell reads it.
    private static final class Parse {
        private final String line;
        private final List<List<String>> commands = new ArrayList<>();
        private final List<String> substitutions = new ArrayList<>();
        private List<String> words = new ArrayList<>();
        private final StringBuilder word = new StringBuilder();
        private boolean inWord;
        private int i;

        Parse(String line) {
            this.line = line;
            while (i < line.length()) {
                char c = line.charAt(i);
                if (Character.isWhitespace(c)) {
                    endWord();
                    i++;
                } else if (c == '#' && !inWord) {
                    break;
                } else if (c == ';' || c == '&' || c == '|' || c == '(' || c == ')') {
                    endCommand();
                    i++;
                } else if (c == '\\') {
                    if (i + 1 < line.length()) {
                        append(line.charAt(i + 1));
                    }
                    i += 2;
                } else if (c == '\'') {
                    int end = line.indexOf('\'', i + 1);
                    end = end < 0 ? line.length() : end;
                    word.append(line, i + 1, end);
                    inWord = true;
                    i = end + 1;
                } else if (c == '"') {
                    i++;
                    doubleQuoted();
                } else if (!substitution()) {
                    append(c);
                    i++;
                }
            }
            endCommand();
        }

        private void doubleQuoted() {
            inWord = true;
            while (i < line.length()) {
                char c = line.charAt(i);
                if (c == '"') {
                    i++;
                    return;
                }
                if (c == '\\'
                        && i + 1 < line.length()
                        && "\"\\$`".indexOf(line.charAt(i + 1)) >= 0) {
                    word.append(line.charAt(i + 1));
                    i += 2;
                } else if (!substitution()) {
                    word.append(c);
                    i++;
                }
            }
        }

        // A $(…) or `…` at i: what it runs is a command of its own, its output unknown.
        private boolean substitution() {
            char c = line.charAt(i);
            if (c == '`') {
                StringBuilder inner = new StringBuilder();
                int j = i + 1;
                while (j < line.length() && line.charAt(j) != '`') {
                    if (line.charAt(j) == '\\' && j + 1 < line.length()) {
                        j++;
                    }
                    inner.append(line.charAt(j++));
                }
                substitutions.add(inner.toString());
                i = j + 1;
            } else if (c == '$' && i + 1 < line.length() && line.charAt(i + 1) == '(') {
                int end = closing(i + 2);
                substitutions.add(line.substring(i + 2, end));
                i = end + 1;
            } else {
                return false;
            }
            append(UNKNOWN);
            return true;
        }

        // The ')' that closes a '(' just before from, past quotes and escapes.
        private int closing(int from) {
            int depth = 1;
            int j = from;
            while (j < line.length()) {
                char c = line.charAt(j);
                if (c == '\\') {
                    j += 2;
                } else if (c == '\'' || c == '"') {
                    j = closingQuote(j);
                } else if (c == '(') {
                    depth++;
                    j++;
                } else if (c == ')' && --depth == 0) {
                    return j;
                } else {
                    j++;
                }
            }
            return line.length();
        }

        private int closingQuote(int open) {
            char quote = line.charAt(open);
            int j = open + 1;
            while (j < line.length() && line.charAt(j) != quote) {
                j += quote == '"' && line.charAt(j) == '\\' ? 2 : 1;
            }
            return j + 1;
        }

        private void append(char c) {
            word.append(c);
            inWord = true;
        }

        private void endWord() {
            if (inWord) {
                words.add(word.toString());
                word.setLength(0);
                inWord = false;
            }
        }

        private void endCommand() {
            endWord();
            if (!words.isEmpty()) {
                commands.add(words);
                words = new ArrayList<>();
            }
        }
    }
}
