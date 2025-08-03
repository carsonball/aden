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
            args?.args as string[] || []
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
                  description: 'Working directory path (e.g., "C:/Users/carso/dev/aden")'
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
          }
        ]
      };
    });
  }

  private async readJsonFile(filepath: string): Promise<CallToolResult> {
    try {
      if (!existsSync(filepath)) {
        return {
          content: [
            {
              type: 'text',
              text: `❌ File not found: ${filepath}`
            }
          ],
          isError: true
        };
      }

      const content = readFileSync(filepath, 'utf8');
      const parsed = JSON.parse(content);
      
      return {
        content: [
          {
            type: 'text',
            text: `✅ Valid JSON file loaded\nFile: ${filepath}\nSize: ${content.length} characters\nKeys: ${Object.keys(parsed).join(', ')}`
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

  private async executeJava(classpath: string, mainClass: string, args: string[] = []): Promise<CallToolResult> {
    return new Promise((resolve) => {
      const javaArgs = ['-cp', classpath, mainClass, ...args];
      const childProcess: ChildProcess = spawn('java', javaArgs, { 
        cwd: process.cwd() // This 'process' is the global Node.js process
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
      if (!existsSync(filepath)) {
        return {
          content: [{ type: 'text', text: `❌ SQL file not found: ${filepath}` }],
          isError: true
        };
      }
  
      const content = readFileSync(filepath, 'utf8');
      
      return {
        content: [
          {
            type: 'text',
            text: `📜 SQL Script: ${filepath}\n` +
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
      writeFileSync(filepath, content, 'utf8');
      
      return {
        content: [
          {
            type: 'text',
            text: `✅ File written successfully\n` +
                  `Path: ${filepath}\n` +
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

  async run() {
    const transport = new StdioServerTransport();
    await this.server.connect(transport);
  }
}

// Start the server
const server = new AdenDevMcpServer();
server.run().catch(console.error);