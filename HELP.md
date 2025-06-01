# Exception Analyzer

Инструмент для анализа исключений в Java проектах Spring Boot.

## Описание

Скрипт анализирует все Java файлы в подкаталогах указанной директории (исключая папки `target` и `test`) и находит все места, где бросаются исключения. Результат сохраняется в HTML файл с двумя таблицами:

1. **Детальная таблица исключений** - содержит информацию о каждом найденном исключении
2. **Сводная таблица** - количество исключений по проектам и файлам

## Возможности

- Парсинг Java файлов с помощью JavaParser
- Обработка строковых констант, переменных и явно указанных строк
- Корректная обработка исключений с несколькими параметрами
- Исключение папок `target` и `test` из анализа
- Сортировка результатов по проекту, файлу и номеру строки
- Генерация HTML отчета с красивым оформлением

## Требования

- Java 11 или выше
- Maven 3.6 или выше

## Сборка

```bash
mvn clean compile package
```

## Использование

### Вариант 1: Использование Maven Exec Plugin

```bash
mvn exec:java -Dexec.args="/path/to/your/projects/directory"
```

### Вариант 2: Запуск JAR файла

```bash
java -jar target/exception-analyzer-1.0.0-shaded.jar /path/to/your/projects/directory
```

### Вариант 3: Прямой запуск класса

```bash
java -cp target/classes:target/lib/* com.analyzer.ExceptionAnalyzer /path/to/your/projects/directory
```

## Структура проекта

```
exception-analyzer/
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── analyzer/
│                   └── ExceptionAnalyzer.java
├── pom.xml
└── README.md
```

## Результат

После выполнения скрипта будет создан файл `exception_analysis_report.html` в текущей директории.

Файл содержит:
- **Сводную таблицу** с колонками: Имя проекта, Имя файла, Количество исключений
- **Детальную таблицу** с колонками: Имя проекта, Имя файла, Тип исключения, Текст исключения

## Примеры обрабатываемых исключений

Скрипт корректно обрабатывает различные формы исключений:

```java
// Простое исключение без параметров
throw new RuntimeException();

// Исключение с строковым сообщением
throw new IllegalArgumentException("Invalid parameter");

// Исключение с переменной
throw new CustomException(errorMessage);

// Исключение с несколькими параметрами
throw new ValidationException(field, value, "must not be null");

// Исключение с константой
throw new ServiceException(ERROR_CODE_INVALID_INPUT);

// Переброс существующего исключения
throw existingException;
```

## Технические детали

- Использует JavaParser для корректного парсинга Java кода
- Обходит AST (Abstract Syntax Tree) для поиска ThrowStmt узлов
- Извлекает информацию о типе исключения и его параметрах
- Игнорирует каталоги `target` и `test` на всех уровнях вложенности
- Сортирует результаты для удобного просмотра