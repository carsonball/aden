# CLAUDE.md - AI Assistant Guidelines

## Overview

This document provides guidelines for working with Claude on the .NET Framework to AWS NoSQL Migration Analyzer project. It ensures consistent, efficient, and high-quality AI-assisted development.

## Project Context

**Project**: .NET Framework to AWS NoSQL Migration Analyzer  
**Purpose**: Analyze legacy .NET applications and recommend AWS NoSQL migration strategies  
**Tech Stack**: Java 17, Maven, JSqlParser, OpenAI API  
**Key Components**: SQL DDL Parser, Entity Framework Parser, LINQ Analyzer, AI Recommendation Engine

## Communication Guidelines

### 1. Context Preservation

When starting a new conversation:
```
I'm working on the .NET Framework to AWS NoSQL Migration Analyzer project.
Current focus: [specific component/feature]
Previous work: [what was accomplished]
Current issue: [what needs to be done]
```

### 2. Code Reference Format

When referencing existing code:
```
In SchemaParser.java (line ~120), the method convertTable() does X.
I need to modify it to handle Y.
```

### 3. Error Reporting Template

```
Error: [error message]
File: [filename and line number]
Context: [what you were trying to do]
What I've tried: [debugging steps taken]
```

## Code Standards

### 1. Java Style Guide

- **Package**: All code under `org.carball.aden`
- **Naming**: CamelCase for classes, camelCase for methods/variables
- **Annotations**: Use Lombok where appropriate (@Data, @Builder, @Slf4j)
- **Logging**: Use SLF4J with appropriate levels (trace, debug, info, warn, error)

### 2. Testing Standards

```java
// Test naming: should[ExpectedBehavior]When[Condition]
@Test
public void shouldParseForeignKeyWhenReferencesClausePresent() {
    // Given - setup
    // When - action
    // Then - assertions with AssertJ
}
```

### 3. Documentation

```java
/**
 * Parses SQL DDL and extracts database schema.
 *
 * @param ddlContent SQL DDL statements
 * @return DatabaseSchema containing tables and relationships
 * @throws IllegalArgumentException if DDL is invalid
 */
public DatabaseSchema parseDDL(String ddlContent) {
```

## Task Templates

### 1. Bug Fix Request

```
BUG: [Component] - [Brief description]

Steps to reproduce:
1. [Step 1]
2. [Step 2]

Expected: [what should happen]
Actual: [what actually happens]

Relevant code: [file and method]
```

### 2. Feature Enhancement

```
FEATURE: [Component] - [Brief description]

Current behavior: [how it works now]
Desired behavior: [how it should work]
Use case: [why this is needed]

Constraints:
- [Any limitations to consider]
- [Backward compatibility needs]
```

### 3. Refactoring Request

```
REFACTOR: [Component] - [Goal]

Current issues:
- [Problem 1]
- [Problem 2]

Proposed approach: [if you have ideas]
Must maintain: [what cannot change]
```

## Development Workflow Templates

For feature development, follow the Design → Implement → Test → Refactor workflow using these templates:

### 1. Design Phase

```
DESIGN: [Component] - [What we're designing]

Requirements:
- [Functional requirement 1]
- [Non-functional requirement 1]

Constraints:
- [Technical constraints]
- [Integration constraints]

Need help with:
- Class/interface design
- Pattern selection
- API design
- Trade-off analysis
```

### 2. Implementation Phase

```
IMPLEMENT: [Component] - [Specific piece]

Design decision: [Reference the agreed design]
Current task: [Specific implementation step]
Dependencies: [What this relies on]

Need:
- [Specific class/method implementation]
- [Following the agreed patterns]
```

### 3. Test Phase

```
TEST: [Component] - [What we're testing]

Implementation complete: [What was built]
Test scenarios needed:
- [Scenario 1]
- [Edge case 1]
- [Error case 1]

Test approach:
- Unit tests for [components]
- Integration tests for [flows]
- Mock requirements: [what to mock]
```

### 4. Refactor Phase

```
REFACTOR: [Component] - [Improvement goal]

Current implementation: [Brief description]
Issues identified:
- [From testing]
- [From code review]
- [From usage]

Proposed improvements:
- [Improvement 1]
- [Maintain: what must not break]

Success criteria:
- All tests still pass
- [Specific improvement metric]
```

### Workflow Example

```
1. Start with DESIGN to establish architecture
2. IMPLEMENT in small increments
3. TEST each increment thoroughly
4. REFACTOR when patterns emerge or issues are found
5. Repeat steps 2-4 until feature is complete
```

### When to Move Between Phases

- Design → Implement: When you have clear interfaces and patterns decided
- Implement → Test: When a logical unit of functionality is complete
- Test → Refactor: When all tests pass but code could be cleaner
- Refactor → Implement: When refactoring is complete and tests still pass

### Workflow Anti-patterns

- ❌ Don't skip design phase for complex features
- ❌ Don't implement everything before testing anything
- ❌ Don't refactor without tests
- ❌ Don't mix phases (e.g., refactoring during implementation)

## Best Practices for Claude Interactions

### 1. Incremental Development

- Request small, focused changes
- Test each change before requesting the next
- Build features incrementally

### 2. Code Review Requests

```
Please review this code for:
- [ ] Correctness
- [ ] Performance implications  
- [ ] Error handling
- [ ] Edge cases
- [ ] Testing approach
```

### 3. Architecture Decisions

When asking for architectural advice:
- Provide current structure
- Explain constraints
- Define success criteria
- Ask for trade-offs analysis

## Common Requests

### 1. Parser Enhancement

```
The [Parser] currently handles [X].
I need it to also handle [Y].
Example input: [provide example]
Expected output: [show desired result]
```

### 2. Test Case Generation

```
Please generate test cases for [Component.method()].
Focus on:
- Edge cases
- Error conditions  
- Performance boundaries
Current test coverage: [X%]
```

### 3. Performance Optimization

```
Component: [name]
Current performance: [metrics]
Bottleneck: [identified issue]
Constraints: [memory/time limits]
```

### 4. Compilation/Build Issues

```
Error: [exact error message]
File: [location]
Java version: [version being used]
What I've tried: [steps taken]
```

## Project-Specific Guidelines

### 1. Parser Modifications

- Always update corresponding tests
- Consider SQL dialect variations
- Maintain JSqlParser integration
- Document regex patterns

### 2. AI Integration

- Mock OpenAI responses in tests
- Handle API failures gracefully
- Consider response parsing variations
- Document prompt changes
- Support -Dskip.ai=true for development/testing

### 3. Analysis Engine

- Maintain scoring consistency
- Document business rules
- Consider edge cases in pattern matching
- Update complexity calculations carefully

## Anti-Patterns to Avoid

### 1. Don't Request

- ❌ "Rewrite the entire module"
- ❌ "Make it work" (too vague)
- ❌ "Fix all the bugs"

### 2. Do Request

- ✅ "Add null check in method X for parameter Y"
- ✅ "Enhance parser to handle COMPUTE columns"
- ✅ "Add test case for circular foreign keys"

## Debugging Collaboration

### 1. Effective Debugging Request

```
ISSUE: [Specific problem]

Debug info:
- Input: [what goes in]
- Output: [what comes out]
- Expected: [what should come out]
- Logs: [relevant log entries]

What I've verified:
- [Check 1]
- [Check 2]
```

### 2. Stack Trace Sharing

```
Exception: [Exception type]
Message: [Error message]
Critical line: [The important part]
[Include only relevant portions of stack trace]
```

## Version Control Integration

When requesting code changes:
- Specify if you need a full file or just a diff
- Mention if you're working on a feature branch
- Note any merge conflicts to consider

## Testing Strategy

### 1. Unit Test Requests

```
Need unit tests for: [Component.method]
Scenarios to cover:
- Happy path: [description]
- Edge case 1: [description]
- Error case: [description]
Mock requirements: [what needs mocking]
```

### 2. Integration Test Requests

```
Integration test needed:
Start: [entry point]
End: [expected outcome]
Components involved: [list]
External dependencies: [what to mock]
```

## Performance Considerations

When working on performance-sensitive code:
- State performance requirements upfront
- Provide current benchmarks
- Specify acceptable trade-offs
- Note memory constraints

## Security Considerations

For security-related changes:
- Highlight sensitive data handling
- Note authentication/authorization needs
- Specify input validation requirements
- Consider injection attack vectors

## Continuous Improvement

### 1. Feedback Format

```
What worked well: [specific examples]
What could improve: [specific suggestions]
Feature request: [new capability that would help]
```

### 2. Learning Optimization

- Save successful patterns
- Document solved problems
- Build on previous solutions
- Reference past conversations when relevant

## Quick Reference

### Common Files
- `SchemaParser.java` - SQL DDL parsing with JSqlParser
- `EFModelParser.java` - C# Entity Framework model detection
- `LinqAnalyzer.java` - LINQ query pattern analysis
- `DotNetPatternAnalyzer.java` - Pattern correlation logic
- `RecommendationEngine.java` - AI integration for recommendations
- `DotNetAnalyzerCLI.java` - Command line interface and main entry point
- `MigrationReport.java` - Output generation in JSON/Markdown formats

### Key Patterns
- Navigation properties indicate relationships
- Include() calls suggest denormalization candidates
- High frequency + always together = good migration candidate
- Complex relationships → DocumentDB, Simple → DynamoDB, Graph patterns → Neptune
- Package-private methods for test access (remove 'private' modifier)

### Development Workflow Commands
```
# Design phase - get architecture right first
DESIGN: Ask for patterns, interfaces, and trade-offs

# Implementation phase - build incrementally  
IMPLEMENT: Build one component at a time

# Test phase - verify behavior
TEST: Comprehensive test cases for current implementation

# Refactor phase - improve without breaking
REFACTOR: Clean up with all tests passing
```

### Testing Commands
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=SchemaParserTest

# Run minimal tests
mvn test -Dtest=MinimalTests

# Test with sample application (no OpenAI key needed)
java -Dskip.ai=true -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar samples/TestEcommerceApp/schema.sql samples/TestEcommerceApp/

# Run with real AI recommendations
export OPENAI_API_KEY=sk-...
java -jar target/dotnet-aws-migration-analyzer-1.0.0-jar-with-dependencies.jar samples/TestEcommerceApp/schema.sql samples/TestEcommerceApp/
```

---

*Remember: Clear communication leads to better code. When in doubt, over-communicate context and constraints.*