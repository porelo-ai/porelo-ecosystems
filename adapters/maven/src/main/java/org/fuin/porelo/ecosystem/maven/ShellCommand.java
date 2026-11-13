package org.fuin.porelo.ecosystem.maven;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The settings file a step's script passes to Maven, read as a shell splits it: commands at {@code
 * ;}, {@code &}, {@code |} and parentheses, words at unquoted spaces with quotes and backslashes
 * taken away, what {@code $(…)} and backticks run as commands of their own, and Maven only where it
 * is the command, never an argument of another one. A {@code cd} moves where the next commands run;
 * the file is answered from the checkout's root.
 */
final class ShellCommand {
    private static final Set<String> MAVEN = Set.of("mvn", "mvnw", "mvnd");
    private static final Set<String> SHELLS = Set.of("sh", "bash", "dash", "zsh");
    // Commands that move where the next ones run.
    private static final Set<String> MOVES = Set.of("cd", "pushd", "popd");
    // Words that open and close a condition or a loop, under which a cd may or may not run.
    private static final Set<String> OPENS =
            Set.of("if", "while", "until", "for", "case", "select");
    private static final Set<String> CLOSES = Set.of("fi", "done", "esac");
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

    // Where the commands run, relative to the checkout's root; null where that is not known.
    private @Nullable String directory;
    private boolean tooDeep;
    // The directories pushd left, the last on top.
    private List<@Nullable String> pushed = new ArrayList<>();
    // How many conditions and loops the lines read so far are in.
    private int control;
    private List<Heredoc> heredocs = List.of();

    /**
     * A script that starts in a directory of the checkout.
     *
     * @param directory relative to the checkout's root, empty for the root, null when not known
     */
    ShellCommand(@Nullable String directory) {
        this.directory = directory;
    }

    /**
     * A directory as a workflow names it, relative to the checkout's root.
     *
     * @param written the directory as written, null for none
     * @param base where it is relative to, null when not known
     * @return the directory; null when it is not known or leaves the checkout
     */
    static @Nullable String directory(@Nullable String written, @Nullable String base) {
        if (written == null) {
            return base;
        }
        if (base == null
                || written.isEmpty()
                || written.indexOf(UNKNOWN) >= 0
                || written.contains("$")
                || written.startsWith("~")
                || written.startsWith("/")
                || written.contains("\\")) {
            return null;
        }
        Path moved = Path.of(base).resolve(written).normalize();
        return moved.startsWith("..") ? null : moved.toString();
    }

    /**
     * The settings file a line passes to Maven, answered from the checkout's root.
     *
     * @param line one logical line of shell, its continuations joined and its comment cut
     * @return the file; empty when no command of the line runs Maven with one, or when it is
     *     relative to a directory not known
     */
    Optional<String> settingsFile(String line) {
        return settingsFile(line, 0);
    }

    /**
     * Whether a command of the lines read since the last ask was nested too deep to read.
     *
     * @return whether one was, the answer then reset
     */
    boolean tooDeep() {
        boolean was = tooDeep;
        tooDeep = false;
        return was;
    }

    /**
     * The heredocs the line read last opens, in order.
     *
     * @return the heredocs; empty when it opens none
     */
    List<Heredoc> heredocs() {
        return heredocs;
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

    private Optional<String> settingsFile(String line, int depth) {
        if (depth > MAX_DEPTH) {
            tooDeep = true;
            return Optional.empty();
        }
        Parse parse = new Parse(line);
        if (depth == 0) {
            heredocs = List.copyOf(parse.heredocs);
        }
        Optional<String> first = Optional.empty();
        // The directories the open subshells started in, outermost first.
        List<@Nullable String> subshells = new ArrayList<>();
        List<Command> commands = parse.commands;
        for (int k = 0; k < commands.size(); k++) {
            Command command = commands.get(k);
            while (subshells.size() < command.parens()) {
                subshells.add(directory);
            }
            while (subshells.size() > command.parens()) {
                directory = subshells.remove(subshells.size() - 1);
            }
            // The words are expanded before the command runs, from where it runs.
            for (String inner : command.substitutions()) {
                Optional<String> named = apart(inner, depth);
                first = first.isPresent() ? first : named;
            }
            @Nullable Command next = k + 1 < commands.size() ? commands.get(k + 1) : null;
            Optional<String> named = ofCommand(command, next, depth);
            first = first.isPresent() ? first : named;
        }
        if (!subshells.isEmpty()) {
            directory = subshells.get(0);
        }
        return first;
    }

    // A script run by a process of its own: a cd in it does not move the line's commands.
    private Optional<String> apart(String script, int depth) {
        @Nullable String here = directory;
        List<@Nullable String> pushedHere = new ArrayList<>(pushed);
        int controlHere = control;
        try {
            return settingsFile(script, depth + 1);
        } finally {
            directory = here;
            pushed = pushedHere;
            control = controlHere;
        }
    }

    private Optional<String> ofCommand(Command command, @Nullable Command next, int depth) {
        List<String> words = command.words();
        int i = 0;
        while (i < words.size()) {
            String word = words.get(i);
            if (i == 0 && OPENS.contains(word)) {
                control++;
            } else if (i == 0 && CLOSES.contains(word)) {
                control = Math.max(0, control - 1);
            }
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
        if (MOVES.contains(name)) {
            move(name, words.subList(i + 1, words.size()), sure(command, next, i));
            return Optional.empty();
        }
        if (SHELLS.contains(name)) {
            return script(words, i + 1).flatMap(s -> apart(s, depth));
        }
        if (!MAVEN.contains(name)) {
            return Optional.empty();
        }
        for (int k = i + 1; k < words.size(); k++) {
            String word = words.get(k);
            if ((word.equals("-s") || word.equals("--settings")) && k + 1 < words.size()) {
                return fromRoot(words.get(k + 1));
            }
            if (word.startsWith("--settings=")) {
                return fromRoot(word.substring("--settings=".length()));
            }
        }
        return Optional.empty();
    }

    // Whether a cd surely runs, and in the shell itself: not under a condition, a loop, a
    // pipeline or the background, unless what follows it on failure only ends the script.
    private boolean sure(Command command, @Nullable Command next, int at) {
        if (control > 0 || at > 0) {
            return false;
        }
        boolean before = Set.of("", ";", "(").contains(command.before());
        String after = command.after();
        boolean ends =
                after.equals("||")
                        && next != null
                        && !next.words().isEmpty()
                        && Set.of("exit", "return").contains(next.words().get(0));
        return before && (Set.of("", ";", "&&", ")").contains(after) || ends);
    }

    private void move(String name, List<String> arguments, boolean sure) {
        if (!sure || arguments.size() > 1 || (name.equals("popd") && !arguments.isEmpty())) {
            directory = null;
            pushed.clear();
            return;
        }
        if (name.equals("popd")) {
            directory = pushed.isEmpty() ? null : pushed.remove(pushed.size() - 1);
            return;
        }
        @Nullable String target =
                arguments.isEmpty() || arguments.get(0).equals("-") ? null : arguments.get(0);
        if (name.equals("pushd")) {
            pushed.add(directory);
        }
        directory = target == null ? null : directory(target, directory);
    }

    // A file as Maven, run in the directory, reads it; an absolute one is left as written.
    private Optional<String> fromRoot(String file) {
        if (file.startsWith("/")) {
            return Optional.of(file);
        }
        if (directory == null) {
            return Optional.empty();
        }
        return Optional.of(directory.isEmpty() ? file : directory + "/" + file);
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

    /**
     * A heredoc a line opens: the lines after it up to its end are its input, no commands.
     *
     * @param end the line that ends it
     * @param tabs whether tabs before the end are taken away, as {@code <<-} does
     */
    record Heredoc(String end, boolean tabs) {
        /**
         * Whether a line ends the heredoc.
         *
         * @param line a line after the one that opened it
         * @return whether it is the end
         */
        boolean endsAt(String line) {
            return (tabs ? line.replaceFirst("^\\t+", "") : line).equals(end);
        }
    }

    // A command with what comes before and after it, and how deep in parentheses it runs.
    private record Command(
            List<String> words,
            List<String> substitutions,
            String before,
            String after,
            int parens) {}

    // One pass over a line as a shell reads it.
    private static final class Parse {
        private final String line;
        private final List<Command> commands = new ArrayList<>();
        private final List<Heredoc> heredocs = new ArrayList<>();
        private List<String> words = new ArrayList<>();
        private List<String> substitutions = new ArrayList<>();
        private final StringBuilder word = new StringBuilder();
        private boolean inWord;
        private String before = "";
        private int parens;
        private int heredoc; // 0, or 1 for << and 2 for <<- waiting for the end's word
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
                    String op =
                            line.startsWith("&&", i) || line.startsWith("||", i)
                                    ? line.substring(i, i + 2)
                                    : line.startsWith(";;", i) ? ";" : String.valueOf(c);
                    i += line.startsWith(";;", i) ? 2 : op.length();
                    endCommand(op);
                    if (op.equals("(")) {
                        parens++;
                    } else if (op.equals(")")) {
                        parens = Math.max(0, parens - 1);
                    }
                } else if (line.startsWith("<<<", i)) {
                    endWord();
                    i += 3;
                } else if (line.startsWith("<<", i)) {
                    endWord();
                    i += 2;
                    heredoc = 1;
                    if (i < line.length() && line.charAt(i) == '-') {
                        heredoc = 2;
                        i++;
                    }
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
            endCommand("");
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
                if (heredoc > 0) {
                    heredocs.add(new Heredoc(word.toString(), heredoc == 2));
                    heredoc = 0;
                } else {
                    words.add(word.toString());
                }
                word.setLength(0);
                inWord = false;
            }
        }

        private void endCommand(String after) {
            endWord();
            if (!words.isEmpty() || !substitutions.isEmpty()) {
                commands.add(new Command(words, substitutions, before, after, parens));
                words = new ArrayList<>();
                substitutions = new ArrayList<>();
            }
            before = after;
        }
    }
}
