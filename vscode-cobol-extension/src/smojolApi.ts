import axios, { AxiosInstance } from 'axios';
import * as vscode from 'vscode';

/**
 * Client pour l'API Smojol MCP
 */
export class SmojolApiClient {
    private client: AxiosInstance;
    private baseUrl: string;

    constructor() {
        this.baseUrl = this.getApiUrl();
        this.client = axios.create({
            baseURL: this.baseUrl,
            timeout: 5000,
            headers: {
                'Content-Type': 'application/json'
            }
        });
    }

    private getApiUrl(): string {
        const config = vscode.workspace.getConfiguration('cobolSmojol');
        return config.get<string>('apiUrl') || 'http://localhost:8080';
    }

    /**
     * Recherche un copybook par nom
     */
    async searchCopybook(name: string): Promise<CopybookInfo | null> {
        try {
            const response = await this.client.get(`/api/copybooks/${name}`);
            return response.data;
        } catch (error) {
            console.error(`Error searching copybook ${name}:`, error);
            return null;
        }
    }

    /**
     * Recherche un programme par nom
     */
    async searchProgram(name: string): Promise<ProgramInfo | null> {
        try {
            const response = await this.client.get(`/api/programs/${name}`);
            return response.data;
        } catch (error) {
            console.error(`Error searching program ${name}:`, error);
            return null;
        }
    }

    /**
     * Trouve les usages d'un copybook
     */
    async findCopybookUsage(name: string): Promise<string[]> {
        try {
            const response = await this.client.get(`/api/copybooks/${name}/usage`);
            return response.data.programs || [];
        } catch (error) {
            console.error(`Error finding copybook usage ${name}:`, error);
            return [];
        }
    }

    /**
     * Recherche un JCL par nom
     */
    async searchJcl(name: string): Promise<JclInfo | null> {
        try {
            const response = await this.client.get(`/api/jcl/${name}`);
            return response.data;
        } catch (error) {
            console.error(`Error searching JCL ${name}:`, error);
            return null;
        }
    }

    /**
     * Vérifie la santé de l'API
     */
    async checkHealth(): Promise<boolean> {
        try {
            const response = await this.client.get('/api/health');
            return response.status === 200;
        } catch (error) {
            return false;
        }
    }
}

/**
 * Interfaces pour les données de l'API
 */
export interface CopybookInfo {
    name: string;
    path: string;
    size: number;
    lines: number;
    fields?: string[];
    includes?: string[];
    used_by_cbl?: string[];
    used_by_cpy?: string[];
    parse_status?: string;
}

export interface ProgramInfo {
    name: string;
    path: string;
    size: number;
    lines: number;
    copybooks?: Array<{ name: string; line: number }>;
    calls?: string[];
    called_by?: string[];
    jcls?: string[];
    complexity?: number;
}

export interface JclInfo {
    name: string;
    path: string;
    job_name?: string;
    programs: string[];
    dd_names?: string[];
    steps?: Array<{ step: string; program: string }>;
}
