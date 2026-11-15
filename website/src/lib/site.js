export const site = {
	name: 'AEA',
	title: 'AEA documentation',
	description: "Documentation for AEA, an addon for Overgrown's Origins on NeoForge 1.21.1.",
	repo: 'https://github.com/AlphaSuckss/AEA-NeoForge',
	/** Where the Markdown lives in the repository, for "Edit this page" links. */
	contentPath: 'website/src/content'
};

/** Sidebar sections, in order. `dir` is the folder under src/content ('' is the top level). */
export const sections = [
	{ dir: '', label: 'Getting started' },
	{ dir: 'powers', label: 'Power types' }
];
