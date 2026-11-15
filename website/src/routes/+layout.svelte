<script>
	import '../app.css';

	import { afterNavigate } from '$app/navigation';
	import { base } from '$app/paths';
	import { page } from '$app/state';
	import Nav from '$lib/components/Nav.svelte';
	import { site } from '$lib/site.js';

	let { children } = $props();
	let menu = $state();
	let dark = $state(false);

	const current = $derived((page.params.slug ?? '').replace(/\/$/, ''));

	function readTheme() {
		const set = document.documentElement.dataset.theme;
		dark = set ? set === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
	}

	function toggleTheme() {
		dark = !dark;
		document.documentElement.dataset.theme = dark ? 'dark' : 'light';
		try {
			localStorage.setItem('aea-theme', dark ? 'dark' : 'light');
		} catch (e) {}
	}

	/** Adds a Copy button to every code block on the page. */
	function addCopyButtons() {
		for (const pre of document.querySelectorAll('.prose pre')) {
			if (pre.parentElement.classList.contains('code-block')) continue;
			const wrap = document.createElement('div');
			wrap.className = 'code-block';
			pre.replaceWith(wrap);
			wrap.append(pre);
			const button = document.createElement('button');
			button.type = 'button';
			button.className = 'copy';
			button.textContent = 'Copy';
			button.addEventListener('click', async () => {
				await navigator.clipboard.writeText(pre.textContent);
				button.textContent = 'Copied';
				setTimeout(() => (button.textContent = 'Copy'), 1500);
			});
			wrap.append(button);
		}
	}

	afterNavigate(() => {
		if (menu) menu.open = false;
		readTheme();
		addCopyButtons();
	});
</script>

<header class="site-header">
	<a class="brand" href="{base}/">
		<img src="{base}/wordmark.png" alt="AEA" width="76" height="28" />
		<span>docs</span>
	</a>
	<button class="theme-toggle" type="button" onclick={toggleTheme}>{dark ? 'Light theme' : 'Dark theme'}</button>
	<a class="header-link" href={site.repo} target="_blank" rel="noopener">GitHub</a>
</header>

<details class="menu" bind:this={menu}>
	<summary>All pages</summary>
	<Nav {current} />
</details>

<div class="frame">
	<aside class="sidebar">
		<Nav {current} />
	</aside>
	{@render children()}
</div>

<footer class="site-footer">
	AEA is made by AlphaSucks and released under the MIT license.
</footer>
