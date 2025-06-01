package com.analyzer.exceptionanalyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.utils.SourceRoot;

import java.io.*;
import java.nio.file.*;
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

            // Генерируем HTML отчет
            generateHtmlReport(exceptions, projectSummaries);

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

    private void generateHtmlReport(List<ExceptionRecord> exceptions, List<ProjectSummary> summaries) {
        try (PrintWriter writer = new PrintWriter(new FileWriter("exception_analysis_report.html"))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html>");
            writer.println("<head>");
            writer.println("    <meta charset='UTF-8'>");
            writer.println("    <title>Exception Analysis Report</title>");
            writer.println("    <style>");
            writer.println("        body { font-family: Arial, sans-serif; margin: 20px; }");
            writer.println("        table { border-collapse: collapse; width: 100%; margin: 20px 0; }");
            writer.println("        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
            writer.println("        th { background-color: #f2f2f2; font-weight: bold; }");
            writer.println("        tr:nth-child(even) { background-color: #f9f9f9; }");
            writer.println("        .summary { margin-bottom: 40px; }");
            writer.println("        h1, h2 { color: #333; }");
            writer.println("    </style>");
            writer.println("</head>");
            writer.println("<body>");

            writer.println("<h1>Exception Analysis Report</h1>");
            writer.println("<p>Generated on: " + new Date() + "</p>");

            // Таблица сводки
            writer.println("<div class='summary'>");
            writer.println("<h2>Summary by Project and File</h2>");
            writer.println("<table>");
            writer.println("<tr><th>Project Name</th><th>File Name</th><th>Exception Count</th></tr>");

            for (ProjectSummary summary : summaries) {
                writer.printf("<tr><td>%s</td><td>%s</td><td>%d</td></tr>%n",
                        escapeHtml(summary.getProjectName()),
                        escapeHtml(summary.getFileName()),
                        summary.getExceptionCount());
            }
            writer.println("</table>");
            writer.println("</div>");

            // Детальная таблица исключений
            writer.println("<h2>Detailed Exception Analysis</h2>");
            writer.println("<table>");
            writer.println("<tr><th>Project Name</th><th>File Name</th><th>Exception Type</th><th>Exception Text</th></tr>");

            for (ExceptionRecord exception : exceptions) {
                writer.printf("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>%n",
                        escapeHtml(exception.getProjectName()),
                        escapeHtml(exception.getFileName()),
                        escapeHtml(exception.getExceptionType()),
                        escapeHtml(exception.getExceptionText()));
            }

            writer.println("</table>");
            writer.println("</body>");
            writer.println("</html>");

            System.out.println("HTML report generated: exception_analysis_report.html");

        } catch (IOException e) {
            System.err.println("Error generating HTML report: " + e.getMessage());
        }
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

            if (n.getExpression() instanceof ObjectCreationExpr) {
                ObjectCreationExpr expr = (ObjectCreationExpr) n.getExpression();
                String exceptionType = expr.getType().getNameAsString();
                String exceptionText = buildExceptionText(expr);
                int lineNumber = n.getBegin().map(pos -> pos.line).orElse(0);

                exceptions.add(new ExceptionRecord(
                        projectName, fileName, exceptionType, exceptionText, lineNumber
                ));
            } else {
                // Обработка случаев, когда бросается переменная исключения
                String exceptionText = n.getExpression().toString();
                String exceptionType = "Unknown"; // Тип неизвестен для переменных
                int lineNumber = n.getBegin().map(pos -> pos.line).orElse(0);

                exceptions.add(new ExceptionRecord(
                        projectName, fileName, exceptionType, exceptionText, lineNumber
                ));
            }
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