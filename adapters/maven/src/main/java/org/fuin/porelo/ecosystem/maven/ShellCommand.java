package org.fuin.porelo.ecosystem.maven;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The settings file a step's script passes to Maven, read as the step's shell splits it: commands
 * at its separators, words at unquoted spaces with quotes and escapes taken away, what {@code $(…)}
 * runs as commands of their own, and Maven only where it is the command, never an argument of
 * another one. A {@code cd} moves where the next commands run; the file is answered from where the
 * job's steps start.
 */
final class ShellCommand {
    private static final Set<String> MAVEN = Set.of("mvn", "mvnw", "mvnd");
    private static final Set<String> SHELLS = Set.of("sh", "bash", "dash", "zsh");
    // Commands that move where the next ones run, under the name each stands for.
    private static final Map<String, String> MOVES =
            Map.of("cd", "cd", "pushd", "pushd", "popd", "popd");
    private static final Map<String, String> WINDOWS_MOVES =
            Map.of(
                    "cd", "cd",
                    "chdir", "cd",
                    "sl", "cd",
                    "set-location", "cd",
                    "pushd", "pushd",
                    "push-location", "pushd",
                    "popd", "popd",
                    "pop-location", "popd");
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
    private static final Map<String, Set<String>> CMD_WRAPPERS = Map.of("call", Set.of());
    // Commands that always succeed, so a command after them and && surely runs.
    private static final Set<List<String>> SUCCEEDS = Set.of(List.of("true"), List.of(":"));
    private static final Pattern ASSIGNMENT =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*=.*", Pattern.DOTALL);
    private static final Pattern WINDOWS_EXECUTABLE = Pattern.compile("\\.(cmd|bat|exe)$");
    // What a substitution's output stands in for: unknown, and no file name.
    private static final char UNKNOWN = '\0';
    private static final int MAX_DEPTH = 4;

    /** How a step's shell reads its script. */
    enum Shell {
        /** {@code bash} and {@code sh}, the shell a step runs in unless it names another. */
        POSIX('\\'),
        /** {@code pwsh} and {@code powershell}. */
        POWERSHELL('`'),
        /** {@code cmd}. */
        CMD('^');

        private final char escape;

        Shell(char escape) {
            this.escape = escape;
        }

        /**
         * The character that takes the next one as written, and at a line's end joins the next.
         *
         * @return the character
         */
        char escape() {
            return escape;
        }

        /**
         * The shell a workflow's {@code shell} names.
         *
         * @param written the value as written, null where none is set
         * @return the shell; empty for a script that is no shell's, or a shell not read
         */
        static Optional<Shell> named(@Nullable String written) {
            if (written == null) {
                return Optional.of(POSIX);
            }
            String name = written.strip().split("\\s+", -1)[0].toLowerCase(Locale.ROOT);
            return switch (name) {
                case "bash", "sh" -> Optional.of(POSIX);
                case "pwsh", "powershell" -> Optional.of(POWERSHELL);
                case "cmd" -> Optional.of(CMD);
                default -> Optional.empty();
            };
        }
    }

    /**
     * A settings file a line passes to Maven.
     *
     * @param file the file, from where the job's steps start
     * @param at where in the line the word that names it starts
     */
    record Named(String file, int at) {}

    private final Shell shell;
    // Where the commands run, relative to where the steps start; null where that is not known.
    private @Nullable String directory;
    private boolean tooDeep;
    // The directories pushd left, the last on top.
    private List<@Nullable String> pushed = new ArrayList<>();
    // The directories the subshells still open after the lines read so far started in.
    private final List<@Nullable String> subshells = new ArrayList<>();
    // How many PowerShell blocks the lines read so far are in.
    private int braces;
    // How many conditions and loops the lines read so far are in.
    private int control;
    private List<Heredoc> heredocs = List.of();

    /**
     * A script that starts in a directory of the workspace.
     *
     * @param directory relative to where the steps start, empty there, null when not known
     * @param shell the shell that runs the script
     */
    ShellCommand(@Nullable String directory, Shell shell) {
        this.directory = directory;
        this.shell = shell;
    }

    /**
     * A directory as a workflow names it, relative to where the steps start.
     *
     * @param written the directory as written, null for none
     * @param base where it is relative to, null when not known
     * @return the directory; null when it is not known or leaves the workspace
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
     * The settings file a line passes to Maven, answered from where the steps start.
     *
     * @param line one logical line of the script, its continuations joined and its comment cut
     * @return the file; empty when no command of the line runs Maven with one, or when it is
     *     relative to a directory not known
     */
    Optional<Named> settingsFile(String line) {
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
     * Whether a line goes on in the next one, by the shell's escape at its end.
     *
     * @param line one line without its comment
     * @return whether it goes on
     */
    boolean continues(String line) {
        return !line.isEmpty() && line.charAt(line.length() - 1) == shell.escape();
    }

    /**
     * A line without its comment: from a {@code #} that starts a word outside quotes; {@code cmd}
     * has no such comment.
     *
     * @param line one line
     * @return the line up to its comment
     */
    String withoutComment(String line) {
        if (shell == Shell.CMD) {
            return line;
        }
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote == '\'') {
                quote = c == '\'' ? 0 : quote;
            } else if (c == shell.escape()) {
                i++;
            } else if (quote == '"') {
                quote = c == '"' ? 0 : quote;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (shell == Shell.POWERSHELL && line.startsWith("<#", i)) {
                // A block comment's end is no comment of its own; one open past the line stays.
                int end = line.indexOf("#>", i + 2);
                if (end < 0) {
                    return line;
                }
                i = end + 1;
            } else if (c == '#' && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private Optional<Named> settingsFile(String line, int depth) {
        if (depth > MAX_DEPTH) {
            tooDeep = true;
            return Optional.empty();
        }
        // Only a POSIX shell runs parentheses apart; their directories carry over its lines.
        boolean apartInParens = shell == Shell.POSIX;
        List<@Nullable String> open = depth == 0 ? subshells : new ArrayList<>();
        Parse parse = new Parse(line, shell, open.size(), depth == 0 ? braces : 0);
        if (depth == 0) {
            heredocs = List.copyOf(parse.heredocs);
            braces = parse.braces;
        }
        Optional<Named> first = Optional.empty();
        List<Command> commands = parse.commands;
        for (int k = 0; k < commands.size(); k++) {
            Command command = commands.get(k);
            if (apartInParens) {
                enter(open, command.parens());
            }
            // The words are expanded before the command runs, from where it runs.
            for (int s = 0; s < command.substitutions().size(); s++) {
                int at = command.substitutionStarts().get(s);
                Optional<Named> named =
                        apart(command.substitutions().get(s), depth)
                                .map(n -> new Named(n.file(), at));
                first = first.isPresent() ? first : named;
            }
            Optional<Named> named = ofCommand(commands, k, depth);
            first = first.isPresent() ? first : named;
        }
        if (apartInParens) {
            if (depth == 0) {
                enter(open, parse.parens);
            } else if (!open.isEmpty()) {
                directory = open.get(0);
            }
        }
        return first;
    }

    // Opens or closes subshells up to a depth, each closed one back where it started.
    private void enter(List<@Nullable String> open, int parens) {
        while (open.size() < parens) {
            open.add(directory);
        }
        while (open.size() > parens) {
            directory = open.remove(open.size() - 1);
        }
    }

    // A script run by a process of its own: a cd in it does not move the line's commands.
    private Optional<Named> apart(String script, int depth) {
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

    private Optional<Named> ofCommand(List<Command> commands, int k, int depth) {
        Command command = commands.get(k);
        List<String> words = command.words();
        Map<String, Set<String>> wrappers =
                switch (shell) {
                    case POSIX -> WRAPPERS;
                    case CMD -> CMD_WRAPPERS;
                    case POWERSHELL -> Map.of();
                };
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
            Set<String> withArgument = wrappers.get(name(word));
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
        @Nullable String joined = joinedTarget(words.get(i));
        if (joined != null) {
            List<String> targets = new ArrayList<>(List.of(joined));
            targets.addAll(words.subList(i + 1, words.size()));
            move("cd", arguments(targets), sure(commands, k, i));
            return Optional.empty();
        }
        String name = name(words.get(i));
        @Nullable String move = (shell == Shell.POSIX ? MOVES : WINDOWS_MOVES).get(name);
        if (move != null) {
            move(move, arguments(words.subList(i + 1, words.size())), sure(commands, k, i));
            return Optional.empty();
        }
        if (shell == Shell.POSIX && SHELLS.contains(name)) {
            int at = script(words, i + 1);
            return at < 0
                    ? Optional.empty()
                    : apart(words.get(at), depth)
                            .map(n -> new Named(n.file(), command.starts().get(at)));
        }
        if (!MAVEN.contains(name)) {
            return Optional.empty();
        }
        for (int w = i + 1; w < words.size(); w++) {
            String word = words.get(w);
            int at = command.starts().get(w);
            if ((word.equals("-s") || word.equals("--settings")) && w + 1 < words.size()) {
                return fromStart(words.get(w + 1), command.starts().get(w + 1));
            }
            if (word.startsWith("--settings=")) {
                return fromStart(word.substring("--settings=".length()), at);
            }
            // Maven takes the rest of -s joined to it as its value, but -s= as no file here.
            if (word.startsWith("-s") && word.length() > 2 && word.charAt(2) != '=') {
                return fromStart(word.substring(2), at);
            }
        }
        return Optional.empty();
    }

    // The target of a cd a Windows shell takes joined to its name, as in cd.. or cd\; null for
    // any other word.
    private @Nullable String joinedTarget(String word) {
        List<String> names =
                switch (shell) {
                    case CMD -> List.of("chdir", "cd");
                    case POWERSHELL -> List.of("cd", "sl");
                    case POSIX -> List.of();
                };
        for (String name : names) {
            if (word.length() > name.length()
                    && word.regionMatches(true, 0, name, 0, name.length())
                    && "./\\".indexOf(word.charAt(name.length())) >= 0) {
                return word.substring(name.length());
            }
        }
        return null;
    }

    // A command's name as the shell finds it: Windows shells ignore case and the extension.
    private String name(String word) {
        if (shell == Shell.POSIX) {
            int slash = word.lastIndexOf('/');
            return slash < 0 ? word : word.substring(slash + 1);
        }
        int slash = Math.max(word.lastIndexOf('/'), word.lastIndexOf('\\'));
        String name = (slash < 0 ? word : word.substring(slash + 1)).toLowerCase(Locale.ROOT);
        return WINDOWS_EXECUTABLE.matcher(name).replaceFirst("");
    }

    // The arguments of a cd, without the options that only say how to name its target.
    private List<String> arguments(List<String> words) {
        List<String> arguments = new ArrayList<>();
        for (String word : words) {
            String option = word.toLowerCase(Locale.ROOT);
            boolean naming =
                    switch (shell) {
                        case CMD -> option.equals("/d");
                        case POWERSHELL -> option.equals("-path") || option.equals("-literalpath");
                        case POSIX -> false;
                    };
            if (!naming) {
                arguments.add(path(word));
            }
        }
        return arguments;
    }

    // A path as written, with the separator Windows shells also take.
    private String path(String word) {
        if (shell == Shell.POSIX) {
            return word;
        }
        // A drive, or a provider such as env:, is no place in the workspace.
        return word.indexOf(':') >= 0 ? String.valueOf(UNKNOWN) : word.replace('\\', '/');
    }

    // Whether a cd surely runs, and in the shell itself: not under a condition, a loop, a
    // pipeline or the background, unless what follows it on failure only ends the script.
    private boolean sure(List<Command> commands, int k, int at) {
        Command command = commands.get(k);
        // Parentheses of a Windows shell group commands that run in the shell itself, and a
        // PowerShell block may run any number of times.
        if (control > 0
                || at > 0
                || (shell != Shell.POSIX && command.parens() > 0)
                || command.braces() > 0) {
            return false;
        }
        String before = command.before();
        for (int j = k; shell == Shell.POSIX && before.equals("&&") && j > 0; j--) {
            Command previous = commands.get(j - 1);
            if (!SUCCEEDS.contains(previous.words()) || !previous.substitutions().isEmpty()) {
                break;
            }
            before = previous.before();
        }
        @Nullable Command next = k + 1 < commands.size() ? commands.get(k + 1) : null;
        String after = command.after();
        boolean ends =
                after.equals("||")
                        && next != null
                        && !next.words().isEmpty()
                        && Set.of("exit", "return").contains(next.words().get(0));
        return Set.of("", ";", "(").contains(before)
                && (Set.of("", ";", "&&", ")").contains(after) || ends);
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
    private Optional<Named> fromStart(String written, int at) {
        String file = path(written);
        if (file.startsWith("/")) {
            return Optional.of(new Named(file, at));
        }
        if (directory == null) {
            return Optional.empty();
        }
        return Optional.of(new Named(directory.isEmpty() ? file : directory + "/" + file, at));
    }

    // The word of the script a shell is given with -c, alone or among other short options as in
    // -ec; -1 for none.
    private static int script(List<String> words, int from) {
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
        return command && k < words.size() ? k : -1;
    }

    /**
     * Lines a line opens whose lines up to an end are no commands: a heredoc's input, a PowerShell
     * here-string or block comment.
     *
     * @param end the text that ends it
     * @param kind where in a line the end stands
     */
    record Heredoc(String end, Kind kind) {
        /** Where in a line the end of a heredoc stands. */
        enum Kind {
            /** The whole line, as {@code <<} reads it. */
            LINE,
            /** The whole line after leading tabs, as {@code <<-} reads it. */
            TABBED_LINE,
            /** At the line's start, as a here-string ends. */
            LINE_START,
            /** Anywhere in the line, as a block comment ends. */
            WITHIN
        }

        /**
         * Whether a line ends the heredoc.
         *
         * @param line a line after the one that opened it
         * @return whether it is the end
         */
        boolean endsAt(String line) {
            return switch (kind) {
                case LINE -> line.equals(end);
                case TABBED_LINE -> line.replaceFirst("^\\t+", "").equals(end);
                case LINE_START -> line.startsWith(end);
                case WITHIN -> line.contains(end);
            };
        }
    }

    // A command, where its words start in the line, what comes before and after it, and how deep
    // in parentheses it runs.
    private record Command(
            List<String> words,
            List<Integer> starts,
            List<String> substitutions,
            List<Integer> substitutionStarts,
            String before,
            String after,
            int parens,
            int braces) {}

    // One pass over a line as a shell reads it.
    private static final class Parse {
        private final String line;
        private final Shell shell;
        private final List<Command> commands = new ArrayList<>();
        private final List<Heredoc> heredocs = new ArrayList<>();
        private List<String> words = new ArrayList<>();
        private List<Integer> starts = new ArrayList<>();
        private List<String> substitutions = new ArrayList<>();
        private List<Integer> substitutionStarts = new ArrayList<>();
        private final StringBuilder word = new StringBuilder();
        private boolean inWord;
        private int start;
        private String before = "";
        private int parens;
        private int braces;
        private int heredoc; // 0, or 1 for << and 2 for <<- waiting for the end's word
        private int i;

        Parse(String line, Shell shell, int parens, int braces) {
            this.line = line;
            this.shell = shell;
            this.parens = parens;
            this.braces = braces;
            while (i < line.length()) {
                char c = line.charAt(i);
                boolean atCommand = !inWord && words.isEmpty();
                if (Character.isWhitespace(c)) {
                    endWord();
                    i++;
                } else if (shell == Shell.CMD && atCommand && remark()) {
                    break;
                } else if (shell == Shell.CMD && atCommand && c == '@') {
                    i++; // only keeps the command from being echoed
                } else if (shell != Shell.CMD && c == '#' && !inWord) {
                    break;
                } else if (shell == Shell.POWERSHELL && !inWord && line.startsWith("<#", i)) {
                    if (!blockComment()) {
                        break;
                    }
                } else if (shell == Shell.POWERSHELL && hereString()) {
                    break;
                } else if (separator(c)) {
                    String op =
                            line.startsWith("&&", i) || line.startsWith("||", i)
                                    ? line.substring(i, i + 2)
                                    : line.startsWith(";;", i) ? ";" : String.valueOf(c);
                    i += line.startsWith(";;", i) ? 2 : op.length();
                    endCommand(op);
                    if (op.equals("{")) {
                        this.braces++;
                    } else if (op.equals("}")) {
                        this.braces = Math.max(0, this.braces - 1);
                    } else if (op.equals("(")) {
                        this.parens++;
                    } else if (op.equals(")")) {
                        this.parens = Math.max(0, this.parens - 1);
                    }
                } else if (shell == Shell.POSIX && line.startsWith("<<<", i)) {
                    endWord();
                    i += 3;
                } else if (shell == Shell.POSIX && line.startsWith("<<", i)) {
                    endWord();
                    i += 2;
                    heredoc = 1;
                    if (i < line.length() && line.charAt(i) == '-') {
                        heredoc = 2;
                        i++;
                    }
                } else {
                    if (!inWord) {
                        start = i;
                    }
                    wordPart(c);
                }
            }
            endCommand("");
        }

        private boolean separator(char c) {
            return (c == ';' && shell != Shell.CMD)
                    || c == '&'
                    || c == '|'
                    || c == '('
                    || c == ')'
                    || (shell == Shell.POWERSHELL && (c == '{' || c == '}'));
        }

        private void wordPart(char c) {
            if (shell == Shell.POWERSHELL && line.startsWith("${", i)) {
                // A variable named in braces, no block.
                int end = line.indexOf('}', i);
                i = end < 0 ? line.length() : end + 1;
                append(UNKNOWN);
            } else if (c == shell.escape()) {
                if (i + 1 < line.length()) {
                    append(line.charAt(i + 1));
                }
                i += 2;
            } else if (c == '\'' && shell != Shell.CMD) {
                singleQuoted();
            } else if (c == '"') {
                i++;
                doubleQuoted();
            } else if (shell == Shell.CMD && c == '%') {
                append(UNKNOWN); // a variable, expanded before the command runs
                i++;
            } else if (!substitution()) {
                append(c);
                i++;
            }
        }

        // A REM or :: at a command's start makes the rest of the line a comment.
        private boolean remark() {
            int after = i + 3;
            return line.startsWith("::", i)
                    || (line.regionMatches(true, i, "rem", 0, 3)
                            && (after == line.length()
                                    || Character.isWhitespace(line.charAt(after))));
        }

        // A <# … #> at i: skipped, or false when it goes on past the line.
        private boolean blockComment() {
            int end = line.indexOf("#>", i + 2);
            if (end < 0) {
                heredocs.add(new Heredoc("#>", Heredoc.Kind.WITHIN));
                return false;
            }
            i = end + 2;
            return true;
        }

        // A here-string opened at i, the last on the line: its lines are text, no commands.
        private boolean hereString() {
            if (!(line.startsWith("@'", i) || line.startsWith("@\"", i))) {
                return false;
            }
            // Read up to the first other character only, so a long line is read once.
            for (int j = i + 2; j < line.length(); j++) {
                if (!Character.isWhitespace(line.charAt(j))) {
                    return false;
                }
            }
            if (!inWord) {
                start = i;
            }
            heredocs.add(new Heredoc(line.charAt(i + 1) + "@", Heredoc.Kind.LINE_START));
            append(UNKNOWN);
            i = line.length();
            return true;
        }

        private void singleQuoted() {
            inWord = true;
            int j = i + 1;
            while (j < line.length()) {
                char c = line.charAt(j);
                if (c == '\'') {
                    // PowerShell writes a quote inside single quotes as two.
                    if (shell == Shell.POWERSHELL
                            && j + 1 < line.length()
                            && line.charAt(j + 1) == '\'') {
                        word.append('\'');
                        j += 2;
                        continue;
                    }
                    break;
                }
                word.append(c);
                j++;
            }
            i = j + 1;
        }

        private void doubleQuoted() {
            inWord = true;
            while (i < line.length()) {
                char c = line.charAt(i);
                boolean hasNext = i + 1 < line.length();
                if (c == '"') {
                    if (shell == Shell.POWERSHELL && hasNext && line.charAt(i + 1) == '"') {
                        word.append('"');
                        i += 2;
                        continue;
                    }
                    i++;
                    return;
                }
                if (shell == Shell.POSIX
                        && c == '\\'
                        && hasNext
                        && "\"\\$`".indexOf(line.charAt(i + 1)) >= 0) {
                    word.append(line.charAt(i + 1));
                    i += 2;
                } else if (shell == Shell.POWERSHELL && c == '`' && hasNext) {
                    word.append(line.charAt(i + 1));
                    i += 2;
                } else if (shell == Shell.CMD && c == '%') {
                    word.append(UNKNOWN);
                    i++;
                } else if (!substitution()) {
                    word.append(c);
                    i++;
                }
            }
        }

        // A $(…), or in a POSIX shell `…`, at i: what it runs is a command of its own, its
        // output unknown.
        private boolean substitution() {
            char c = line.charAt(i);
            int at = i;
            if (c == '`' && shell == Shell.POSIX) {
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
            } else if (c == '$'
                    && shell != Shell.CMD
                    && i + 1 < line.length()
                    && line.charAt(i + 1) == '(') {
                int end = closing(i + 2);
                substitutions.add(line.substring(i + 2, end));
                i = end + 1;
            } else {
                return false;
            }
            substitutionStarts.add(at);
            append(UNKNOWN);
            return true;
        }

        // The ')' that closes a '(' just before from, past quotes and escapes.
        private int closing(int from) {
            int depth = 1;
            int j = from;
            while (j < line.length()) {
                char c = line.charAt(j);
                if (c == shell.escape()) {
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
                j += quote == '"' && line.charAt(j) == shell.escape() ? 2 : 1;
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
                    heredocs.add(
                            new Heredoc(
                                    word.toString(),
                                    heredoc == 2 ? Heredoc.Kind.TABBED_LINE : Heredoc.Kind.LINE));
                    heredoc = 0;
                } else {
                    words.add(word.toString());
                    starts.add(start);
                }
                word.setLength(0);
                inWord = false;
            }
        }

        private void endCommand(String after) {
            endWord();
            if (!words.isEmpty() || !substitutions.isEmpty()) {
                commands.add(
                        new Command(
                                words,
                                starts,
                                substitutions,
                                substitutionStarts,
                                before,
                                after,
                                parens,
                                braces));
                words = new ArrayList<>();
                starts = new ArrayList<>();
                substitutions = new ArrayList<>();
                substitutionStarts = new ArrayList<>();
            }
            before = after;
        }
    }
}
