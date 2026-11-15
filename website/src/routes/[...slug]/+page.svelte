<script>
	import { base } from '$app/paths';
	import { href } from '$lib/docs.js';
	import { site } from '$lib/site.js';

	let { data } = $props();

	const doc = $derived(data.page);
	const Content = $derived(data.content);
	const editUrl = $derived(`${site.repo}/edit/main/${site.contentPath}${doc.file.replace('/src/content', '')}`);
</script>

<svelte:head>
	<title>{doc.slug ? `${doc.title} | ${site.title}` : site.title}</title>
	<meta name="description" content={doc.description || site.description} />
</svelte:head>

<div class="main">
	<article>
		<header class="page-head">
			<h1>{doc.title}</h1>
			{#if doc.description}<p class="lede">{doc.description}</p>{/if}
			{#if doc.typeId}
				<div class="item-tooltip">
					<div class="item-tooltip-body">
						<span class="item-tooltip-name">{doc.typeId}</span>
						<span class="item-tooltip-kind">Power type</span>
						<span class="item-tooltip-mod">AEA</span>
					</div>
				</div>
			{/if}
		</header>

		{#if doc.headings.length > 1}
			<details class="toc-inline">
				<summary>On this page</summary>
				<ul>
					{#each doc.headings as heading}
						<li class="depth-{heading.depth}"><a href="#{heading.id}">{heading.label}</a></li>
					{/each}
				</ul>
			</details>
		{/if}

		<div class="prose">
			<Content />
		</div>

		<footer class="page-foot">
			<div class="pager">
				{#if data.previous}
					<a href={href(base, data.previous)}><small>Previous</small>{data.previous.navTitle}</a>
				{/if}
				{#if data.next}
					<a class="next" href={href(base, data.next)}><small>Next</small>{data.next.navTitle}</a>
				{/if}
			</div>
			<p class="edit"><a href={editUrl} target="_blank" rel="noopener">Edit this page on GitHub</a></p>
		</footer>
	</article>

	{#if doc.headings.length > 1}
		<aside class="toc" aria-label="On this page">
			<p class="toc-title">On this page</p>
			<ul>
				{#each doc.headings as heading}
					<li class="depth-{heading.depth}"><a href="#{heading.id}">{heading.label}</a></li>
				{/each}
			</ul>
		</aside>
	{/if}
</div>
