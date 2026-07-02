# Java Application Guide - Java Web Server

## 1. Architecture Overview

This is a Java 8 web application server

### 1.1 Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 8 |
| Build Tool | Maven |
| Web App Framework | Spring Boot |
| UI Framework | PrimeFaces |
| Input Handling | Java AWT Event Listeners |
| Logging | SLF4J with Logback |
| Data Persistence | Java Serialization or JSON (Gson/Jackson) |
| Testing | JUnit 5 |

## Considerations

The server must be bound to 0.0.0.0 instead of 127.0.0.1.

## 3. Build Commands

### 3.1 Compile the Project

```bash
mvn compile
```

### 3.2 Package as JAR

```bash
mvn package
```

### 3.3 Clean and Build

```bash
mvn clean package
```

### 3.4 Skip Tests During Build

```bash
mvn clean package -DskipTests
```

---

## 4. Execute the Application

### 4.1 Run with Maven

Requirements:

Detect Windows Java installation (e.g., C:\Program Files\Java\...)

Invoke java.exe from WSL using /mnt/c/...

Do NOT use Xvfb

Do NOT use Linux Java

Ensure the application runs the server on Windows using the Windows Java executable.

Show the exact command used

The goal is to run the server directly on Windows.

## 5.1 Testing

Testing Instructions:

Create playwright scripts.
Execute playwright scripts using the playwright mcp by launching the application in a playwright browser.
After all steps of the test case have been completed, save a screenshot.
Use visual verification from the @QATesterVisualCheck.md subagent.

## 10. Related Documentation

- **Business Plan**: `Business_Plan.md` - Business requirements and value proposition
- **High-Level Design**: `HLD.md` - Application architecture overview
- **Low-Level Design**: `LLD.md` - Detailed technical specifications
