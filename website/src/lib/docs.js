import GithubSlugger from 'github-slugger';
import { sections } from './site.js';

const components = import.meta.glob('/src/content/**/*.md');
const sources = import.meta.glob('/src/content/**/*.md', { query: '?raw', import: 'default', eager: true });

function frontmatter(source) {
	const match = /^---\n([\s\S]*?)\n---\n?/.exec(source);
	const data = {};
	if (!match) return { data, body: source };
	for (const line of match[1].split('\n')) {
		const pair = /^([A-Za-z_]+):\s*(.*)$/.exec(line);
		if (pair) data[pair[1]] = pair[2].replace(/^"(.*)"$/, '$1').replace(/\\"/g, '"');
	}
	return { data, body: source.slice(match[0].length) };
}

/** The h2 and h3 headings of a page, with the same ids rehype-slug gives them. */
function headings(body) {
	const slugger = new GithubSlugger();
	const out = [];
	let fenced = false;
	for (const line of body.split('\n')) {
		if (line.startsWith('```')) fenced = !fenced;
		if (fenced) continue;
		const match = /^(#{2,3})\s+(.+?)\s*$/.exec(line);
		if (!match) continue;
		const label = match[2].replace(/`/g, '');
		out.push({ depth: match[1].length, label, id: slugger.slug(label) });
	}
	return out;
}

function toPage(file) {
	const path = file.replace('/src/content/', '').replace(/\.md$/, '');
	const slug = path === 'index' ? '' : path.replace(/\/index$/, '');
	const dir = path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : '';
	const { data, body } = frontmatter(sources[file]);
	return {
		file,
		slug,
		dir,
		title: data.title ?? slug,
		navTitle: data.nav_title ?? data.title ?? slug,
		description: data.description ?? '',
		typeId: data.type_id ?? null,
		order: Number(data.order ?? 100),
		headings: headings(body)
	};
}

const sectionIndex = (dir) => {
	const i = sections.findIndex((s) => s.dir === dir);
	return i < 0 ? sections.length : i;
};

/** Every page, in sidebar order. */
export const pages = Object.keys(sources)
	.map(toPage)
	.sort((a, b) => sectionIndex(a.dir) - sectionIndex(b.dir) || a.order - b.order || a.title.localeCompare(b.title));

/** The sidebar: sections that have at least one page. */
export const nav = sections
	.map((section) => ({ label: section.label, pages: pages.filter((p) => p.dir === section.dir) }))
	.filter((section) => section.pages.length > 0);

export const href = (base, page) => `${base}/${page.slug ? page.slug + '/' : ''}`;

export async function loadComponent(page) {
	return (await components[page.file]()).default;
}
