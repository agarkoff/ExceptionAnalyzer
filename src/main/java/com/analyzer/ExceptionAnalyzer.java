package com.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.utils.SourceRoot;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class ExceptionAnalyzer {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java ExceptionAnalyzer <directory-path>");
            System.exit(1);
        }

        String rootDirectory = args[0];
        Path rootPath = Paths.get(rootDirectory);

        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            System.err.println("Directory does not exist: " + rootDirectory);
            System.exit(1);
        }

        ExceptionAnalyzer analyzer = new ExceptionAnalyzer();
        analyzer.analyzeProjects(rootPath);
    }

    private void analyzeProjects(Path rootPath) {
        List<ExceptionRecord> exceptions = new ArrayList<>();
        List<ProjectSummary> projectSummaries = new ArrayList<>();
        List<ProjectTotal> projectTotals = new ArrayList<>();

        try {
            // Находим все подкаталоги (Spring Boot проекты)
            List<Path> projectDirectories = Files.list(rootPath)
                    .filter(Files::isDirectory)
                    .collect(Collectors.toList());

            for (Path projectDir : projectDirectories) {
                String projectName = projectDir.getFileName().toString();
                System.out.println("Analyzing project: " + projectName);

                List<ExceptionRecord> projectExceptions = analyzeProject(projectDir, projectName);
                exceptions.addAll(projectExceptions);

                // Добавляем общее количество исключений по проекту
                projectTotals.add(new ProjectTotal(projectName, projectExceptions.size()));

                // Группируем по файлам для сводки
                Map<String, Long> fileCounts = projectExceptions.stream()
                        .collect(Collectors.groupingBy(
                                ExceptionRecord::getFileName,
                                Collectors.counting()
                        ));

                for (Map.Entry<String, Long> entry : fileCounts.entrySet()) {
                    projectSummaries.add(new ProjectSummary(
                            projectName,
                            entry.getKey(),
                            entry.getValue().intValue()
                    ));
                }
            }

            // Сортируем результаты
            exceptions.sort(Comparator
                    .comparing(ExceptionRecord::getProjectName)
                    .thenComparing(ExceptionRecord::getFileName)
                    .thenComparingInt(ExceptionRecord::getLineNumber));

            projectSummaries.sort(Comparator
                    .comparing(ProjectSummary::getProjectName)
                    .thenComparing(ProjectSummary::getFileName));

            projectTotals.sort(Comparator
                    .comparing(ProjectTotal::getProjectName));

            // Генерируем HTML отчет
            generateHtmlReport(exceptions, projectSummaries, projectTotals);

            // Генерируем txt файл с уникальными Exception Text
            generateUniqueExceptionTextFile(exceptions);

        } catch (IOException e) {
            System.err.println("Error analyzing projects: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<ExceptionRecord> analyzeProject(Path projectDir, String projectName) {
        List<ExceptionRecord> exceptions = new ArrayList<>();

        try {
            // Находим все Java файлы, исключая target и test каталоги
            Files.walk(projectDir)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("\\target\\"))
                    .filter(path -> !path.toString().contains("/test/"))
                    .filter(path -> !path.toString().contains("\\test\\"))
                    .forEach(javaFile -> {
                        try {
                            analyzeJavaFile(javaFile, projectName, exceptions);
                        } catch (Exception e) {
                            System.err.println("Error analyzing file " + javaFile + ": " + e.getMessage());
                        }
                    });

        } catch (IOException e) {
            System.err.println("Error walking project directory " + projectDir + ": " + e.getMessage());
        }

        return exceptions;
    }

    private void analyzeJavaFile(Path javaFile, String projectName, List<ExceptionRecord> exceptions) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);

            if (cu == null) {
                System.err.println("Failed to parse: " + javaFile);
                return;
            }

            String fileName = javaFile.getFileName().toString();
            ExceptionVisitor visitor = new ExceptionVisitor(projectName, fileName, exceptions);
            visitor.visit(cu, null);

        } catch (IOException e) {
            System.err.println("Error reading file " + javaFile + ": " + e.getMessage());
        }
    }

    private void generateUniqueExceptionTextFile(List<ExceptionRecord> exceptions) {
        try {
            // Собираем уникальные значения Exception Text
            Set<String> uniqueExceptionTexts = exceptions.stream()
                    .map(ExceptionRecord::getExceptionText)
                    .filter(text -> text != null && !text.trim().isEmpty())
                    .collect(Collectors.toCollection(LinkedHashSet::new)); // LinkedHashSet сохраняет порядок

            // Сортируем для удобства чтения
            List<String> sortedUniqueTexts = uniqueExceptionTexts.stream()
                    .sorted()
                    .collect(Collectors.toList());

            // Записываем в файл
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("unique_exception_texts.txt"))) {
                writer.write("Unique Exception Texts\n");
                writer.write("=====================\n");
                writer.write("Generated on: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n");
                writer.write("Total unique exception texts found: " + sortedUniqueTexts.size() + "\n");
                writer.write("\n");

                for (String exceptionText : sortedUniqueTexts) {
                    writer.write(exceptionText);
                    writer.newLine();
                }
            }

            System.out.println("Unique exception texts file generated: unique_exception_texts.txt");
            System.out.println("Total unique exception texts: " + sortedUniqueTexts.size());

        } catch (IOException e) {
            System.err.println("Error generating unique exception texts file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void generateHtmlReport(List<ExceptionRecord> exceptions, List<ProjectSummary> summaries, List<ProjectTotal> projectTotals) {
        try {
            // Создаем объект для передачи данных в шаблон
            Map<String, Object> templateData = new HashMap<>();

            // Базовая информация
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            templateData.put("generatedDate", dateFormat.format(new Date()));

            // Статистика
            templateData.put("totalProjects", projectTotals.size());
            templateData.put("totalFiles", summaries.size());
            templateData.put("totalExceptions", exceptions.size());

            // Данные таблиц
            templateData.put("projectTotals", projectTotals);
            templateData.put("projectSummaries", summaries);
            templateData.put("exceptions", exceptions);

            // Загружаем и обрабатываем шаблон
            MustacheFactory mf = new DefaultMustacheFactory();

            // Пытаемся загрузить шаблон из classpath
            Mustache mustache;
            try (InputStream templateStream = getClass().getClassLoader().getResourceAsStream("report-template.mustache")) {
                if (templateStream != null) {
                    mustache = mf.compile(new InputStreamReader(templateStream), "report-template");
                } else {
                    // Если шаблон не найден в classpath, пытаемся загрузить из файла
                    File templateFile = new File("report-template.mustache");
                    if (templateFile.exists()) {
                        mustache = mf.compile("report-template.mustache");
                    } else {
                        System.err.println("Template file not found. Creating default template...");
                        createDefaultTemplate();
                        mustache = mf.compile("report-template.mustache");
                    }
                }
            }

            // Генерируем HTML
            try (FileWriter writer = new FileWriter("exception_analysis_report.html")) {
                mustache.execute(writer, templateData);
                writer.flush();
            }

            System.out.println("HTML report generated: exception_analysis_report.html");

        } catch (IOException e) {
            System.err.println("Error generating HTML report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createDefaultTemplate() throws IOException {
        // Создаем базовый шаблон, если файл не найден
        String defaultTemplate = getDefaultTemplate();
        try (FileWriter writer = new FileWriter("report-template.mustache")) {
            writer.write(defaultTemplate);
        }
        System.out.println("Created default template: report-template.mustache");
    }

    private String getDefaultTemplate() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>Exception Analysis Report</title>\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; margin: 20px; }\n" +
                "        table { border-collapse: collapse; width: 100%; margin: 20px 0; }\n" +
                "        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n" +
                "        th { background-color: #f2f2f2; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>Exception Analysis Report</h1>\n" +
                "    <p>Generated on: {{generatedDate}}</p>\n" +
                "    \n" +
                "    <h2>Statistics</h2>\n" +
                "    <p>Projects: {{totalProjects}}, Files: {{totalFiles}}, Exceptions: {{totalExceptions}}</p>\n" +
                "    \n" +
                "    <h2>Project Totals</h2>\n" +
                "    <table>\n" +
                "        <tr><th>Project</th><th>Total</th></tr>\n" +
                "        {{#projectTotals}}\n" +
                "        <tr><td>{{projectName}}</td><td>{{totalExceptions}}</td></tr>\n" +
                "        {{/projectTotals}}\n" +
                "    </table>\n" +
                "    \n" +
                "    <h2>File Summary</h2>\n" +
                "    <table>\n" +
                "        <tr><th>Project</th><th>File</th><th>Count</th></tr>\n" +
                "        {{#projectSummaries}}\n" +
                "        <tr><td>{{projectName}}</td><td>{{fileName}}</td><td>{{exceptionCount}}</td></tr>\n" +
                "        {{/projectSummaries}}\n" +
                "    </table>\n" +
                "    \n" +
                "    <h2>Detailed Analysis</h2>\n" +
                "    <table>\n" +
                "        <tr><th>Project</th><th>File</th><th>Type</th><th>Text</th></tr>\n" +
                "        {{#exceptions}}\n" +
                "        <tr><td>{{projectName}}</td><td>{{fileName}}</td><td>{{exceptionType}}</td><td>{{exceptionText}}</td></tr>\n" +
                "        {{/exceptions}}\n" +
                "    </table>\n" +
                "</body>\n" +
                "</html>";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // Класс для записи об исключении
    public static class ExceptionRecord {
        private final String projectName;
        private final String fileName;
        private final String exceptionType;
        private final String exceptionText;
        private final int lineNumber;

        public ExceptionRecord(String projectName, String fileName, String exceptionType,
                               String exceptionText, int lineNumber) {
            this.projectName = projectName;
            this.fileName = fileName;
            this.exceptionType = exceptionType;
            this.exceptionText = exceptionText;
            this.lineNumber = lineNumber;
        }

        // Геттеры
        public String getProjectName() { return projectName; }
        public String getFileName() { return fileName; }
        public String getExceptionType() { return exceptionType; }
        public String getExceptionText() { return exceptionText; }
        public int getLineNumber() { return lineNumber; }
    }

    // Класс для общих итогов по проекту
    public static class ProjectTotal {
        private final String projectName;
        private final int totalExceptions;

        public ProjectTotal(String projectName, int totalExceptions) {
            this.projectName = projectName;
            this.totalExceptions = totalExceptions;
        }

        // Геттеры
        public String getProjectName() { return projectName; }
        public int getTotalExceptions() { return totalExceptions; }
    }

    // Класс для сводки по проекту
    public static class ProjectSummary {
        private final String projectName;
        private final String fileName;
        private final int exceptionCount;

        public ProjectSummary(String projectName, String fileName, int exceptionCount) {
            this.projectName = projectName;
            this.fileName = fileName;
            this.exceptionCount = exceptionCount;
        }

        // Геттеры
        public String getProjectName() { return projectName; }
        public String getFileName() { return fileName; }
        public int getExceptionCount() { return exceptionCount; }
    }

    // Visitor для поиска исключений
    private static class ExceptionVisitor extends VoidVisitorAdapter<Void> {
        private final String projectName;
        private final String fileName;
        private final List<ExceptionRecord> exceptions;

        public ExceptionVisitor(String projectName, String fileName, List<ExceptionRecord> exceptions) {
            this.projectName = projectName;
            this.fileName = fileName;
            this.exceptions = exceptions;
        }

        @Override
        public void visit(ThrowStmt n, Void arg) {
            super.visit(n, arg);

            // Обрабатываем только создание новых исключений (ObjectCreationExpr)
            // Исключаем пробрасывание существующих исключений (переменных)
            if (n.getExpression() instanceof ObjectCreationExpr) {
                ObjectCreationExpr expr = (ObjectCreationExpr) n.getExpression();
                String exceptionType = expr.getType().getNameAsString();
                String exceptionText = buildExceptionText(expr);
                int lineNumber = n.getBegin().map(pos -> pos.line).orElse(0);

                exceptions.add(new ExceptionRecord(
                        projectName, fileName, exceptionType, exceptionText, lineNumber
                ));
            }
            // Игнорируем случаи типа "throw e", "throw existingException" и т.д.
        }

        private String buildExceptionText(ObjectCreationExpr expr) {
            StringBuilder text = new StringBuilder();

            if (expr.getArguments().isEmpty()) {
                return "()";
            }

            text.append("(");
            for (int i = 0; i < expr.getArguments().size(); i++) {
                if (i > 0) text.append(", ");
                text.append(expr.getArguments().get(i).toString());
            }
            text.append(")");

            return text.toString();
        }
    }
}