import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { readFileSync, existsSync, writeFileSync } from 'fs';
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
  CallToolResult,
  ListToolsResult
} from '@modelcontextprotocol/sdk/types.js';
import { spawn, ChildProcess } from 'child_process';
import * as sql from 'mssql';
import { resolve, isAbsolute, join } from 'path';
import { readdirSync } from 'fs';

class AdenDevMcpServer {
  private server: Server;

  constructor() {
    this.server = new Server({
      name: "aden-dev-mcp",
      version: "1.0.0"
    }, {
      capabilities: {
        tools: {}
      }
    });

    this.setupTools();
  }

  private resolvePath(filepath: string, workingDir?: string): string {
    // If already absolute, return as is
    if (isAbsolute(filepath)) {
      return filepath;
    }
    // Use provided working directory, or current working directory
    const baseDir = workingDir || process.cwd();
    const resolved = resolve(baseDir, filepath);
    
    // If the file doesn't exist and we're in aden-dev-mcp, try the parent directory
    if (!existsSync(resolved) && process.cwd().includes('aden-dev-mcp') && !workingDir) {
      const parentResolved = resolve(process.cwd(), '..', filepath);
      if (existsSync(parentResolved)) {
        return parentResolved;
      }
    }
    
    return resolved;
  }

  private setupTools() {
    // Tool for reading and validating JSON files
    this.server.setRequestHandler(CallToolRequestSchema, async (request) => {
      const { name, arguments: args } = request.params;

      switch (name) {
        case 'read_json_file':
          return await this.readJsonFile(args?.filepath as string);
        
        case 'execute_java':
          return await this.executeJava(
            args?.classpath as string, 
            args?.mainClass as string, 
            args?.args as string[] || [],
            args?.systemProperties as Record<string, string> || {},
            args?.workingDirectory as string
          );
        case 'read_sql_file':
          return await this.readSqlFile(args?.filepath as string);

        case 'write_file':
          return await this.writeFile(
            args?.filepath as string, 
            args?.content as string
          );
        case 'execute_sql':
          return await this.executeSql(
            args?.connectionString as string,
            args?.sqlScript as string
          );
        case 'execute_maven':
          return await this.executeMaven(
            args?.goals as string[],
            args?.workingDirectory as string,
            args?.options as string[]
          );
        case 'execute_dotnet':
          return await this.executeDotNet(
            args?.command as string,
            args?.workingDirectory as string,
            args?.args as string[] || []
          );
        default:
        throw new Error(`Unknown tool: ${name}`);
      }
    });

    // List available tools
    this.server.setRequestHandler(ListToolsRequestSchema, async (): Promise<ListToolsResult> => {
      return {
        tools: [
          {
            name: 'read_json_file',
            description: 'Read and validate a JSON file',
            inputSchema: {
              type: 'object',
              properties: {
                filepath: { type: 'string', description: 'Path to JSON file' }
              },
              required: ['filepath']
            }
          },
          {
            name: 'execute_java',
            description: 'Execute a Java application with classpath and main class',
            inputSchema: {
              type: 'object',
              properties: {
                classpath: { type: 'string', description: 'Java classpath (jar file or directory)' },
                mainClass: { type: 'string', description: 'Fully qualified main class name' },
                args: { 
                  type: 'array', 
                  items: { type: 'string' }, 
                  description: 'Command line arguments',
                  default: []
                },
                systemProperties: {
                  type: 'object',
                  description: 'System properties (-D flags)',
                  additionalProperties: { type: 'string' },
                  default: {}
                },
                workingDirectory: { 
                  type: 'string', 
                  description: 'Working directory path (e.g., "/path/to/project")'
                }
              },
              required: ['classpath', 'mainClass']
            }
          },
          {
            name: 'read_sql_file',
            description: 'Read and display SQL script contents',
            inputSchema: {
              type: 'object',
              properties: {
                filepath: { type: 'string', description: 'Path to SQL file' }
              },
              required: ['filepath']
            }
          },
          {
            name: 'write_file',
            description: 'Write content to a file',
            inputSchema: {
              type: 'object',
              properties: {
                filepath: { type: 'string', description: 'Path where to save the file' },
                content: { type: 'string', description: 'Content to write to the file' }
              },
              required: ['filepath', 'content']
            }
          },
          {
            name: 'execute_sql',
            description: 'Execute a SQL script against SQL Server',
            inputSchema: {
              type: 'object',
              properties: {
                connectionString: { 
                  type: 'string', 
                  description: 'SQL Server connection string (e.g., "Server=localhost;Database=mydb;Trusted_Connection=true;")'
                },
                sqlScript: { 
                  type: 'string', 
                  description: 'SQL script content to execute'
                }
              },
              required: ['connectionString', 'sqlScript']
            }
          },
          {
            name: 'execute_maven',
            description: 'Execute Maven commands (mvn)',
            inputSchema: {
              type: 'object',
              properties: {
                goals: { 
                  type: 'array',
                  items: { type: 'string' },
                  description: 'Maven goals (e.g., ["clean", "package"])'
                },
                workingDirectory: { 
                  type: 'string', 
                  description: 'Working directory path (e.g., "/path/to/project")'
                },
                options: { 
                  type: 'array',
                  items: { type: 'string' },
                  description: 'Maven options (e.g., ["-DskipTests"])',
                  default: []
                }
              },
              required: ['goals', 'workingDirectory']
            }
          },
          {
            name: 'execute_dotnet',
            description: 'Execute .NET CLI commands (dotnet)',
            inputSchema: {
              type: 'object',
              properties: {
                command: { 
                  type: 'string', 
                  description: '.NET command (e.g., "run", "build", "test")'
                },
                workingDirectory: { 
                  type: 'string', 
                  description: 'Working directory path (e.g., "/path/to/dotnet/project")'
                },
                args: { 
                  type: 'array',
                  items: { type: 'string' },
                  description: 'Additional arguments for the dotnet command',
                  default: []
                }
              },
              required: ['command', 'workingDirectory']
            }
          }
        ]
      };
    });
  }

  private async readJsonFile(filepath: string): Promise<CallToolResult> {
    try {
      const resolvedPath = this.resolvePath(filepath);
      if (!existsSync(resolvedPath)) {
        return {
          content: [
            {
              type: 'text',
              text: `❌ File not found: ${filepath} (resolved to: ${resolvedPath})`
            }
          ],
          isError: true
        };
      }

      const content = readFileSync(resolvedPath, 'utf8');
      const parsed = JSON.parse(content);
      
      return {
        content: [
          {
            type: 'text',
            text: `✅ Valid JSON file loaded\nFile: ${resolvedPath}\nSize: ${content.length} characters\nKeys: ${Object.keys(parsed).join(', ')}`
          }
        ],
        isError: false
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      return {
        content: [
          {
            type: 'text',
            text: `❌ Error reading file: ${errorMessage}`
          }
        ],
        isError: true
      };
    }
  }

  private async executeJava(
    classpath: string, 
    mainClass: string, 
    args: string[] = [],
    systemProperties: Record<string, string> = {},
    workingDirectory?: string
  ): Promise<CallToolResult> {
    return new Promise((resolve) => {
      // Use provided working directory or current working directory
      const cwd = workingDirectory || process.cwd();
      
      // Resolve classpath relative to working directory
      const resolvedClasspath = this.resolvePath(classpath, cwd);
      
      // Build system properties flags
      const sysPropFlags = Object.entries(systemProperties)
        .map(([key, value]) => `-D${key}=${value}`);
      
      // Resolve file paths in args (for .sql, .json files, etc.)
      const resolvedArgs = args.map(arg => {
        // Check if arg looks like a file path (contains extension)
        if (arg.includes('.') && !arg.startsWith('-')) {
          return this.resolvePath(arg, cwd);
        }
        return arg;
      });
      
      // Build full command: java -Dprop=value -cp classpath MainClass args
      const javaArgs = [...sysPropFlags, '-cp', resolvedClasspath, mainClass, ...resolvedArgs];
      
      const childProcess: ChildProcess = spawn('java', javaArgs, { 
        cwd: cwd
      });
      
      let stdout = '';
      let stderr = '';
      
      childProcess.stdout?.on('data', (data: Buffer) => {
        stdout += data.toString();
      });
      
      childProcess.stderr?.on('data', (data: Buffer) => {
        stderr += data.toString();
      });
      
      childProcess.on('close', (code: number | null) => {
        const success = code === 0;
        resolve({
          content: [
            {
              type: 'text',
              text: `🔧 Java Execution Complete\n` +
                    `Command: java ${javaArgs.join(' ')}\n` +
                    `Working Directory: ${cwd}\n` +
                    `Exit Code: ${code} ${success ? '✅' : '❌'}\n\n` +
                    `📤 STDOUT:\n${stdout || '(no output)'}\n\n` +
                    `⚠️  STDERR:\n${stderr || '(no errors)'}`
          }
          ],
          isError: !success
        });
      });
      
      childProcess.on('error', (error: Error) => {
        resolve({
          content: [
            {
              type: 'text',
              text: `❌ Failed to start Java process: ${error.message}`
            }
          ],
          isError: true
        });
      });
    });
  }

  private async readSqlFile(filepath: string): Promise<CallToolResult> {
    try {
      const resolvedPath = this.resolvePath(filepath);
      if (!existsSync(resolvedPath)) {
        return {
          content: [{ type: 'text', text: `❌ SQL file not found: ${filepath} (resolved to: ${resolvedPath})` }],
          isError: true
        };
      }
  
      const content = readFileSync(resolvedPath, 'utf8');
      
      return {
        content: [
          {
            type: 'text',
            text: `📜 SQL Script: ${resolvedPath}\n` +
                  `Size: ${content.length} characters\n` +
                  `Lines: ${content.split('\n').length}\n\n` +
                  `Content:\n${'='.repeat(50)}\n${content}\n${'='.repeat(50)}`
          }
        ],
        isError: false
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      return {
        content: [{ type: 'text', text: `❌ Error reading SQL file: ${errorMessage}` }],
        isError: true
      };
    }
  }
  
  private async writeFile(filepath: string, content: string): Promise<CallToolResult> {
    try {
      const resolvedPath = this.resolvePath(filepath);
      writeFileSync(resolvedPath, content, 'utf8');
      
      return {
        content: [
          {
            type: 'text',
            text: `✅ File written successfully\n` +
                  `Path: ${resolvedPath}\n` +
                  `Size: ${content.length} characters\n` +
                  `Lines: ${content.split('\n').length}`
          }
        ],
        isError: false
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      return {
        content: [{ type: 'text', text: `❌ Error writing file: ${errorMessage}` }],
        isError: true
      };
    }
  }

  private async executeSql(connectionString: string, sqlScript: string): Promise<CallToolResult> {
    try {
      const pool = await sql.connect(connectionString);
      const result = await pool.request().query(sqlScript);
      await pool.close();
  
      return {
        content: [
          {
            type: 'text',
            text: `✅ SQL Execution Complete\n` +
                  `Rows affected: ${result.rowsAffected}\n` +
                  `Records returned: ${result.recordset?.length || 0}\n\n` +
                  `Results:\n${JSON.stringify(result.recordset, null, 2)}`
          }
        ],
        isError: false
      };
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      return {
        content: [{ type: 'text', text: `❌ SQL Error: ${errorMessage}` }],
        isError: true
      };
    }
  }

  private async executeMaven(goals: string[], workingDirectory: string, options: string[] = []): Promise<CallToolResult> {
    return new Promise((resolve) => {
      const mavenArgs = [...goals, ...options];
      const childProcess: ChildProcess = spawn('mvn', mavenArgs, { 
        cwd: workingDirectory,
        shell: true // Important for Windows
      });
      
      let stdout = '';
      let stderr = '';
      
      childProcess.stdout?.on('data', (data: Buffer) => {
        stdout += data.toString();
      });
      
      childProcess.stderr?.on('data', (data: Buffer) => {
        stderr += data.toString();
      });
      
      childProcess.on('close', (code: number | null) => {
        const success = code === 0;
        resolve({
          content: [
            {
              type: 'text',
              text: `🔨 Maven Execution Complete\n` +
                    `Command: mvn ${mavenArgs.join(' ')}\n` +
                    `Working Directory: ${workingDirectory}\n` +
                    `Exit Code: ${code} ${success ? '✅' : '❌'}\n\n` +
                    `📤 OUTPUT:\n${stdout || '(no output)'}\n\n` +
                    `⚠️  ERRORS:\n${stderr || '(no errors)'}`
            }
          ],
          isError: !success
        });
      });
      
      childProcess.on('error', (error: Error) => {
        resolve({
          content: [
            {
              type: 'text',
              text: `❌ Failed to start Maven: ${error.message}`
            }
          ],
          isError: true
        });
      });
    });
  }

  private findCsprojFile(workingDirectory: string): string | null {
    try {
      const files = readdirSync(workingDirectory);
      const csprojFile = files.find(file => file.endsWith('.csproj'));
      return csprojFile ? join(workingDirectory, csprojFile) : null;
    } catch {
      return null;
    }
  }

  private isNetFrameworkProject(csprojPath: string): boolean {
    try {
      const content = readFileSync(csprojPath, 'utf8');
      // Check for .NET Framework indicators
      return content.includes('<TargetFrameworkVersion>') ||
             content.includes('ToolsVersion="15.0"') ||
             content.includes('Microsoft.Common.props') ||
             (!content.includes('<TargetFramework>') && content.includes('<OutputType>Exe</OutputType>'));
    } catch {
      return false;
    }
  }

  private async executeBuildAndRun(workingDirectory: string, runArgs: string[] = []): Promise<CallToolResult> {
    return new Promise((resolve) => {
      // First, build the project
      const buildProcess = spawn('dotnet', ['build'], {
        cwd: workingDirectory,
        shell: true
      });

      let buildStdout = '';
      let buildStderr = '';

      buildProcess.stdout?.on('data', (data: Buffer) => {
        buildStdout += data.toString();
      });

      buildProcess.stderr?.on('data', (data: Buffer) => {
        buildStderr += data.toString();
      });

      buildProcess.on('close', (buildCode: number | null) => {
        if (buildCode !== 0) {
          resolve({
            content: [{
              type: 'text',
              text: `🔷 .NET Framework Build Failed\n` +
                    `Command: dotnet build\n` +
                    `Working Directory: ${workingDirectory}\n` +
                    `Exit Code: ${buildCode} ❌\n\n` +
                    `📤 OUTPUT:\n${buildStdout}\n\n` +
                    `⚠️  ERRORS:\n${buildStderr}`
            }],
            isError: true
          });
          return;
        }

        // Build succeeded, now find and run the exe
        try {
          const csprojFile = this.findCsprojFile(workingDirectory);
          if (!csprojFile) {
            resolve({
              content: [{
                type: 'text',
                text: `❌ No .csproj file found in ${workingDirectory}`
              }],
              isError: true
            });
            return;
          }

          // Extract project name from csproj filename
          const projectName = csprojFile.split(/[/\\]/).pop()?.replace('.csproj', '') || 'Unknown';
          const exePath = join(workingDirectory, 'bin', 'Debug', `${projectName}.exe`);

          // Check if exe exists
          if (!existsSync(exePath)) {
            resolve({
              content: [{
                type: 'text',
                text: `❌ Executable not found: ${exePath}\n` +
                      `Build may have failed or different output path.`
              }],
              isError: true
            });
            return;
          }

          // Run the executable with a timeout and simulated input
          const runProcess = spawn(exePath, runArgs, {
            cwd: workingDirectory,
            stdio: ['pipe', 'pipe', 'pipe'], // Enable stdin for keypress simulation
            shell: true
          });

          let runStdout = '';
          let runStderr = '';

          runProcess.stdout?.on('data', (data: Buffer) => {
            runStdout += data.toString();
          });

          runProcess.stderr?.on('data', (data: Buffer) => {
            runStderr += data.toString();
          });

          // Simulate keypress after a delay to auto-exit
          setTimeout(() => {
            try {
              runProcess.stdin?.write('\n');
              runProcess.stdin?.end();
            } catch (e) {
              // Process may have already ended
            }
          }, 1000);

          runProcess.on('close', (runCode: number | null) => {
            resolve({
              content: [{
                type: 'text',
                text: `🔷 .NET Framework Execution Complete\n` +
                      `Build Command: dotnet build\n` +
                      `Run Command: ${exePath} ${runArgs.join(' ')}\n` +
                      `Working Directory: ${workingDirectory}\n` +
                      `Exit Code: ${runCode} ${runCode === 0 ? '✅' : '❌'}\n\n` +
                      `📤 OUTPUT:\n${runStdout || '(no output)'}\n\n` +
                      `⚠️  ERRORS:\n${runStderr || '(no errors)'}`
              }],
              isError: runCode !== 0
            });
          });

          runProcess.on('error', (error: Error) => {
            resolve({
              content: [{
                type: 'text',
                text: `❌ Failed to run executable: ${error.message}`
              }],
              isError: true
            });
          });

        } catch (error) {
          resolve({
            content: [{
              type: 'text',
              text: `❌ Error preparing executable: ${error instanceof Error ? error.message : 'Unknown error'}`
            }],
            isError: true
          });
        }
      });

      buildProcess.on('error', (error: Error) => {
        resolve({
          content: [{
            type: 'text',
            text: `❌ Failed to start build process: ${error.message}`
          }],
          isError: true
        });
      });
    });
  }

  private async executeDotNet(command: string, workingDirectory: string, args: string[] = []): Promise<CallToolResult> {
    // Special handling for 'run' command with .NET Framework projects
    if (command === 'run') {
      const csprojPath = this.findCsprojFile(workingDirectory);
      if (csprojPath && this.isNetFrameworkProject(csprojPath)) {
        return this.executeBuildAndRun(workingDirectory, args);
      }
    }

    // Standard dotnet CLI execution for modern .NET projects
    return new Promise((resolve) => {
      const dotnetArgs = [command, ...args];
      const childProcess: ChildProcess = spawn('dotnet', dotnetArgs, { 
        cwd: workingDirectory,
        shell: true // Important for Windows
      });
      
      let stdout = '';
      let stderr = '';
      
      childProcess.stdout?.on('data', (data: Buffer) => {
        stdout += data.toString();
      });
      
      childProcess.stderr?.on('data', (data: Buffer) => {
        stderr += data.toString();
      });
      
      childProcess.on('close', (code: number | null) => {
        const success = code === 0;
        resolve({
          content: [
            {
              type: 'text',
              text: `🔷 .NET Execution Complete\n` +
                    `Command: dotnet ${dotnetArgs.join(' ')}\n` +
                    `Working Directory: ${workingDirectory}\n` +
                    `Exit Code: ${code} ${success ? '✅' : '❌'}\n\n` +
                    `📤 OUTPUT:\n${stdout || '(no output)'}\n\n` +
                    `⚠️  ERRORS:\n${stderr || '(no errors)'}`
            }
          ],
          isError: !success
        });
      });
      
      childProcess.on('error', (error: Error) => {
        resolve({
          content: [
            {
              type: 'text',
              text: `❌ Failed to start .NET CLI: ${error.message}`
            }
          ],
          isError: true
        });
      });
    });
  }

  async run() {
    const transport = new StdioServerTransport();
    await this.server.connect(transport);
  }
}

// Start the server
const server = new AdenDevMcpServer();
server.run().catch(console.error);