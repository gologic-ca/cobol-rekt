import * as vscode from 'vscode';
import * as path from 'path';
import { SmojolApiClient, CopybookInfo, ProgramInfo } from './smojolApi';

let apiClient: SmojolApiClient;

export function activate(context: vscode.ExtensionContext) {
    console.log('🚀 COBOL Smojol Navigator activé');

    // Initialiser le client API
    apiClient = new SmojolApiClient();

    // Vérifier la connexion API au démarrage
    checkApiConnection();

    // Enregistrer les providers pour COBOL - avec plusieurs sélecteurs pour compatibilité
    const cobolSelectors: vscode.DocumentSelector = [
        { scheme: 'file', language: 'cobol' },
        { scheme: 'file', pattern: '**/*.{cbl,CBL,cob,COB,cobol,COBOL}' },
        { scheme: 'file', language: 'COBOL' }
    ];
    
    context.subscriptions.push(
        vscode.languages.registerDefinitionProvider(cobolSelectors, new CobolDefinitionProvider()),
        vscode.languages.registerHoverProvider(cobolSelectors, new CobolHoverProvider()),
        vscode.languages.registerReferenceProvider(cobolSelectors, new CobolReferenceProvider())
    );

    // Enregistrer les providers pour JCL
    const jclSelectors: vscode.DocumentSelector = [
        { scheme: 'file', language: 'jcl' },
        { scheme: 'file', pattern: '**/*.{jcl,JCL}' },
        { scheme: 'file', language: 'JCL' }
    ];
    
    context.subscriptions.push(
        vscode.languages.registerDefinitionProvider(jclSelectors, new JclDefinitionProvider()),
        vscode.languages.registerHoverProvider(jclSelectors, new JclHoverProvider())
    );

    // Commande pour tester la connexion API
    context.subscriptions.push(
        vscode.commands.registerCommand('cobolSmojol.checkConnection', async () => {
            await checkApiConnection(true);
        })
    );

    console.log('✅ Tous les providers sont enregistrés');
}

async function checkApiConnection(showMessage: boolean = false) {
    const isHealthy = await apiClient.checkHealth();
    
    if (isHealthy) {
        console.log('✅ API Smojol connectée');
        if (showMessage) {
            vscode.window.showInformationMessage('✅ API Smojol connectée');
        }
    } else {
        console.error('❌ API Smojol non disponible');
        if (showMessage) {
            vscode.window.showErrorMessage('❌ API Smojol non disponible sur http://localhost:8080');
        }
    }
}

/**
 * Provider pour Go-to-Definition dans les fichiers COBOL
 * Permet CTRL+Click sur les copybooks
 */
class CobolDefinitionProvider implements vscode.DefinitionProvider {
    async provideDefinition(
        document: vscode.TextDocument,
        position: vscode.Position,
        token: vscode.CancellationToken
    ): Promise<vscode.Definition | undefined> {
        console.log(`🎯 DefinitionProvider appelé - Fichier: ${document.fileName}, Langue: ${document.languageId}`);
        
        const wordRange = document.getWordRangeAtPosition(position);
        if (!wordRange) {
            console.log('⚠️ Pas de mot détecté à cette position');
            return undefined;
        }

        const word = document.getText(wordRange);
        const line = document.lineAt(position.line).text;
        console.log(`🔤 Mot: "${word}", Ligne: "${line.trim()}"`);

        // Détecter si on est sur un COPY statement
        if (line.match(/^\s*COPY\s+/i) || line.includes('COPY')) {
            console.log(`🔍 Recherche du copybook: ${word}`);
            
            const copybookInfo = await apiClient.searchCopybook(word);
            console.log(`📊 Résultat API copybook:`, copybookInfo);
            
            // Chercher directement le fichier dans le workspace avec le nom
            const uri = await findFileInWorkspace(word, ['.cpy', '.CPY', '.cbl', '.CBL']);
            if (uri) {
                console.log(`✅ Fichier trouvé: ${uri.fsPath}`);
                return new vscode.Location(uri, new vscode.Position(0, 0));
            } else {
                console.log(`❌ Fichier non trouvé dans le workspace: ${word}`);
                // Afficher un message à l'utilisateur
                vscode.window.showWarningMessage(`Copybook "${word}" non trouvé dans le workspace`);
            }
        }

        // Détecter si on est sur un CALL statement (appel de programme)
        if (line.match(/CALL\s+/i)) {
            console.log(`🔍 Recherche du programme: ${word}`);
            
            const programInfo = await apiClient.searchProgram(word);
            console.log(`📊 Résultat API programme:`, programInfo);
            
            // Chercher directement le fichier dans le workspace avec le nom
            const uri = await findFileInWorkspace(word, ['.cbl', '.CBL', '.cob', '.COB']);
            if (uri) {
                console.log(`✅ Fichier trouvé: ${uri.fsPath}`);
                return new vscode.Location(uri, new vscode.Position(0, 0));
            } else {
                console.log(`❌ Fichier non trouvé dans le workspace: ${word}`);
                vscode.window.showWarningMessage(`Programme "${word}" non trouvé dans le workspace`);
            }
        }

        console.log('ℹ️ Aucune action de définition trouvée');
        return undefined;
    }
}

/**
 * Provider pour les info-bulles (Hover) dans les fichiers COBOL
 */
class CobolHoverProvider implements vscode.HoverProvider {
    async provideHover(
        document: vscode.TextDocument,
        position: vscode.Position,
        token: vscode.CancellationToken
    ): Promise<vscode.Hover | undefined> {
        const wordRange = document.getWordRangeAtPosition(position);
        if (!wordRange) {
            return undefined;
        }

        const word = document.getText(wordRange);
        const line = document.lineAt(position.line).text;

        // Hover sur un copybook
        if (line.match(/^\s*COPY\s+/i) || line.includes('COPY')) {
            const copybookInfo = await apiClient.searchCopybook(word);
            if (copybookInfo) {
                return new vscode.Hover(createCopybookMarkdown(copybookInfo));
            }
        }

        // Hover sur un programme appelé
        if (line.match(/CALL\s+/i)) {
            const programInfo = await apiClient.searchProgram(word);
            if (programInfo) {
                return new vscode.Hover(createProgramMarkdown(programInfo));
            }
        }

        return undefined;
    }
}

/**
 * Provider pour Find References dans les fichiers COBOL
 */
class CobolReferenceProvider implements vscode.ReferenceProvider {
    async provideReferences(
        document: vscode.TextDocument,
        position: vscode.Position,
        context: vscode.ReferenceContext,
        token: vscode.CancellationToken
    ): Promise<vscode.Location[] | undefined> {
        const wordRange = document.getWordRangeAtPosition(position);
        if (!wordRange) {
            return undefined;
        }

        const word = document.getText(wordRange);
        const line = document.lineAt(position.line).text;

        // Trouver les références d'un copybook
        if (line.match(/^\s*COPY\s+/i) || line.includes('COPY')) {
            const programs = await apiClient.findCopybookUsage(word);
            const locations: vscode.Location[] = [];

            for (const programName of programs) {
                const programInfo = await apiClient.searchProgram(programName);
                if (programInfo && programInfo.path) {
                    const uri = await findFileInWorkspace(programInfo.path, ['.cbl', '.CBL']);
                    if (uri) {
                        // Trouver la ligne du COPY dans le programme
                        const copybook = programInfo.copybooks?.find(c => c.name === word);
                        const lineNumber = copybook?.line || 0;
                        locations.push(new vscode.Location(uri, new vscode.Position(lineNumber, 0)));
                    }
                }
            }

            return locations;
        }

        return undefined;
    }
}

/**
 * Provider pour Go-to-Definition dans les fichiers JCL
 * Permet CTRL+Click sur les programmes référencés (PGM=)
 */
class JclDefinitionProvider implements vscode.DefinitionProvider {
    async provideDefinition(
        document: vscode.TextDocument,
        position: vscode.Position,
        token: vscode.CancellationToken
    ): Promise<vscode.Definition | undefined> {
        const wordRange = document.getWordRangeAtPosition(position);
        if (!wordRange) {
            return undefined;
        }

        const word = document.getText(wordRange);
        const line = document.lineAt(position.line).text;

        // Détecter PGM=PROGRAMNAME
        if (line.match(/PGM=/i)) {
            console.log(`🔍 Recherche du programme JCL: ${word}`);
            
            const programInfo = await apiClient.searchProgram(word);
            
            // Chercher directement le fichier dans le workspace avec le nom
            const uri = await findFileInWorkspace(word, ['.cbl', '.CBL', '.cob', '.COB']);
            if (uri) {
                console.log(`✅ Fichier trouvé: ${uri.fsPath}`);
                return new vscode.Location(uri, new vscode.Position(0, 0));
            } else {
                console.log(`❌ Fichier non trouvé dans le workspace: ${word}`);
            }
        }

        return undefined;
    }
}

/**
 * Provider pour Hover dans les fichiers JCL
 */
class JclHoverProvider implements vscode.HoverProvider {
    async provideHover(
        document: vscode.TextDocument,
        position: vscode.Position,
        token: vscode.CancellationToken
    ): Promise<vscode.Hover | undefined> {
        const wordRange = document.getWordRangeAtPosition(position);
        if (!wordRange) {
            return undefined;
        }

        const word = document.getText(wordRange);
        const line = document.lineAt(position.line).text;

        // Hover sur un programme dans PGM=
        if (line.match(/PGM=/i)) {
            const programInfo = await apiClient.searchProgram(word);
            if (programInfo) {
                return new vscode.Hover(createProgramMarkdown(programInfo));
            }
        }

        return undefined;
    }
}

/**
 * Utilitaires
 */

async function findFileInWorkspace(fileNameOrPath: string, extensions: string[]): Promise<vscode.Uri | undefined> {
    const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
    if (!workspaceFolder) {
        console.log('⚠️ Aucun workspace ouvert');
        return undefined;
    }

    // Extraire juste le nom du fichier (sans chemin et sans extension)
    const fileName = path.basename(fileNameOrPath);
    const fileNameWithoutExt = fileName.replace(/\.[^/.]+$/, '');
    
    console.log(`🔎 Recherche du fichier: ${fileNameWithoutExt} avec extensions: ${extensions.join(', ')}`);

    // Récupérer les patterns d'exclusion depuis la configuration
    const config = vscode.workspace.getConfiguration('cobolSmojol');
    const excludePatterns = config.get<string[]>('searchExclude') || ['**/node_modules/**', '**/out/**', '**/target/**'];
    const excludeGlob = `{${excludePatterns.join(',')}}`;

    // Chercher avec toutes les extensions possibles dans TOUT le workspace
    for (const ext of extensions) {
        const pattern = `**/${fileNameWithoutExt}${ext}`;
        console.log(`  🔍 Pattern: ${pattern} (excludes: ${excludeGlob})`);
        const files = await vscode.workspace.findFiles(pattern, excludeGlob, 1);
        if (files.length > 0) {
            console.log(`  ✅ Trouvé: ${files[0].fsPath}`);
            return files[0];
        }
    }

    console.log(`  ❌ Aucun fichier trouvé pour: ${fileNameWithoutExt}`);
    return undefined;
}

function createCopybookMarkdown(info: CopybookInfo): vscode.MarkdownString {
    const md = new vscode.MarkdownString();
    md.appendMarkdown(`### 📋 Copybook: \`${info.name}\`\n\n`);
    
    // Déterminer et afficher le type du copybook
    let copybookType = '';
    if (info.parse_status && info.parse_status.trim() !== '') {
        copybookType = info.parse_status;
    } else if (info.fields && info.fields.length > 0) {
        copybookType = 'Data Structure';
    } else if (info.includes && info.includes.length > 0) {
        copybookType = 'Include Collection';
    }
    
    if (copybookType) {
        md.appendMarkdown(`📊 **Type:** ${copybookType}\n\n`);
    }
    
    // Afficher le path seulement s'il existe et n'est pas vide
    if (info.path && info.path.trim() !== '') {
        md.appendMarkdown(`**Path:** ${info.path}\n\n`);
    }
    
    // Afficher la taille et les lignes seulement si disponibles (> 0)
    if (info.size && info.size > 0 && info.lines && info.lines > 0) {
        md.appendMarkdown(`**Size:** ${info.size} bytes | **Lines:** ${info.lines}\n\n`);
    }
    
    // Utiliser used_by_cbl en priorité, sinon chercher dans usedBy (format API)
    const usedBy = info.used_by_cbl || (info as any).usedBy || [];
    
    if (usedBy.length > 0) {
        md.appendMarkdown(`**Used by ${usedBy.length} program(s):**\n`);
        
        // Afficher jusqu'à 15 programmes en liste
        const displayLimit = 15;
        const programsToShow = usedBy.slice(0, displayLimit);
        
        programsToShow.forEach((program: string) => {
            md.appendMarkdown(`- ${program}\n`);
        });
        
        // Afficher le compteur des programmes restants
        if (usedBy.length > displayLimit) {
            const remaining = usedBy.length - displayLimit;
            md.appendMarkdown(`\n*... et ${remaining} autre${remaining > 1 ? 's' : ''} programme${remaining > 1 ? 's' : ''}*\n`);
        }
        md.appendMarkdown(`\n`);
    } else {
        md.appendMarkdown(`**Used by:** No programs found\n\n`);
    }

    if (info.includes && info.includes.length > 0) {
        md.appendMarkdown(`**Includes:** ${info.includes.join(', ')}\n\n`);
    }

    md.appendMarkdown(`---\n`);
    md.appendMarkdown(`💡 *CTRL+Click pour ouvrir le fichier*`);
    
    return md;
}

function createProgramMarkdown(info: ProgramInfo): vscode.MarkdownString {
    const md = new vscode.MarkdownString();
    md.appendMarkdown(`### 📄 Programme: \`${info.name}\`\n\n`);
    md.appendMarkdown(`**Path:** ${info.path}\n\n`);
    md.appendMarkdown(`**Size:** ${info.size} bytes | **Lines:** ${info.lines}\n\n`);
    
    // Copybooks en liste à puces
    if (info.copybooks && info.copybooks.length > 0) {
        md.appendMarkdown(`**Copybooks (${info.copybooks.length}):**\n`);
        
        const displayLimit = 15;
        const copybooksToShow = info.copybooks.slice(0, displayLimit);
        
        copybooksToShow.forEach((c: any) => {
            const name = typeof c === 'string' ? c : (c.name || 'unknown');
            md.appendMarkdown(`- ${name}\n`);
        });
        
        if (info.copybooks.length > displayLimit) {
            const remaining = info.copybooks.length - displayLimit;
            md.appendMarkdown(`\n*... et ${remaining} autre${remaining > 1 ? 's' : ''}*\n`);
        }
        md.appendMarkdown(`\n`);
    }

    // Calls en liste à puces
    if (info.calls && info.calls.length > 0) {
        md.appendMarkdown(`**Calls (${info.calls.length}):**\n`);
        
        const displayLimit = 15;
        const callsToShow = info.calls.slice(0, displayLimit);
        
        callsToShow.forEach((call: string) => {
            md.appendMarkdown(`- ${call}\n`);
        });
        
        if (info.calls.length > displayLimit) {
            const remaining = info.calls.length - displayLimit;
            md.appendMarkdown(`\n*... et ${remaining} autre${remaining > 1 ? 's' : ''}*\n`);
        }
        md.appendMarkdown(`\n`);
    }

    // Called by en liste à puces
    if (info.called_by && info.called_by.length > 0) {
        md.appendMarkdown(`**Called by (${info.called_by.length}):**\n`);
        
        const displayLimit = 15;
        const callersToShow = info.called_by.slice(0, displayLimit);
        
        callersToShow.forEach((caller: string) => {
            md.appendMarkdown(`- ${caller}\n`);
        });
        
        if (info.called_by.length > displayLimit) {
            const remaining = info.called_by.length - displayLimit;
            md.appendMarkdown(`\n*... et ${remaining} autre${remaining > 1 ? 's' : ''}*\n`);
        }
        md.appendMarkdown(`\n`);
    }

    // JCL en liste inline (généralement peu nombreux)
    if (info.jcls && info.jcls.length > 0) {
        md.appendMarkdown(`**JCL (${info.jcls.length}):** ${info.jcls.join(', ')}\n\n`);
    }

    // Complexity score si disponible
    if (info.complexity) {
        md.appendMarkdown(`**Complexity Score:** ${info.complexity}\n\n`);
    }

    md.appendMarkdown(`---\n`);
    md.appendMarkdown(`💡 *CTRL+Click pour ouvrir le fichier*`);
    
    return md;
}

export function deactivate() {
    console.log('👋 COBOL Smojol Navigator désactivé');
}
