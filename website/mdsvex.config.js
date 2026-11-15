import { escapeSvelte } from 'mdsvex';
import { createHighlighter, createCssVariablesTheme } from 'shiki';
import rehypeSlug from 'rehype-slug';

const BASE = process.env.BASE_PATH ?? '';

// Token colors come from CSS variables, so code follows the light and dark themes (see app.css).
const theme = createCssVariablesTheme({ name: 'aea', variablePrefix: '--code-', fontStyle: true });
const highlighter = await createHighlighter({ themes: [theme], langs: ['json', 'toml', 'bash'] });
const LANGS = new Set(highlighter.getLoadedLanguages());

function highlight(code, lang) {
	const html = highlighter.codeToHtml(code.replace(/\n$/, ''), { lang: LANGS.has(lang) ? lang : 'text', theme: 'aea' });
	return `{@html \`${escapeSvelte(html)}\`}`;
}

function visit(node, fn, parent = null, index = 0) {
	fn(node, parent, index);
	(node.children ?? []).forEach((child, i) => visit(child, fn, node, i));
}

const text = (node) => (node.type === 'text' ? node.value : (node.children ?? []).map(text).join(''));

/** Internal links get the site's base path; external links open in a new tab. */
function links() {
	return (tree) =>
		visit(tree, (node) => {
			if (node.tagName !== 'a' || typeof node.properties?.href !== 'string') return;
			const href = node.properties.href;
			if (/^https?:/.test(href)) {
				node.properties.target = '_blank';
				node.properties.rel = 'noopener';
			} else if (href.startsWith('/')) {
				// Pages are served as folders, so "/powers/gui" becomes "/powers/gui/".
				const [, path, rest] = /^([^?#]*)(.*)$/.exec(href);
				node.properties.href = BASE + (path.endsWith('/') ? path : path + '/') + rest;
			}
		});
}

const cells = (row, tag) => (row.children ?? []).filter((cell) => cell.tagName === tag);

/** Copies each column heading onto its cells, so a phone can show a row as a labelled stack. */
function labelCells(table) {
	const rows = [];
	visit(table, (node) => node.tagName === 'tr' && rows.push(node));
	const headings = rows.flatMap((row) => cells(row, 'th')).map((cell) => text(cell).trim());
	for (const row of rows) {
		const data = cells(row, 'td');
		// The first cell names the row and the last one describes it; only the ones between need a label.
		data.slice(1, -1).forEach((cell, i) => {
			if (headings[i + 1]) cell.properties = { ...cell.properties, dataLabel: headings[i + 1] };
		});
	}
}

/** Wide tables scroll sideways inside their own box instead of stretching the page. */
function scrollableTables() {
	return (tree) =>
		visit(tree, (node, parent, index) => {
			if (node.tagName !== 'table' || !parent || parent.properties?.className === 'table-scroll') return;
			labelCells(node);
			parent.children[index] = {
				type: 'element',
				tagName: 'div',
				properties: { className: 'table-scroll', tabIndex: 0 },
				children: [node]
			};
		});
}

/** "> [!NOTE]" and "> [!WARNING]" blockquotes become callouts. */
function callouts() {
	return (tree) =>
		visit(tree, (node) => {
			if (node.tagName !== 'blockquote') return;
			const first = (node.children ?? []).find((child) => child.tagName === 'p');
			const lead = first?.children?.[0];
			if (!lead || lead.type !== 'text') return;
			const match = /^\s*\[!(NOTE|WARNING)\]\s*/.exec(lead.value);
			if (!match) return;
			lead.value = lead.value.slice(match[0].length);
			const kind = match[1].toLowerCase();
			node.tagName = 'aside';
			node.properties = { className: `callout callout-${kind}` };
			node.children.unshift({
				type: 'element',
				tagName: 'p',
				properties: { className: 'callout-title' },
				children: [{ type: 'text', value: kind === 'note' ? 'Note' : 'Warning' }]
			});
		});
}

export default {
	extensions: ['.md'],
	smartypants: false,
	highlight: { highlighter: highlight },
	rehypePlugins: [rehypeSlug, links, scrollableTables, callouts]
};
