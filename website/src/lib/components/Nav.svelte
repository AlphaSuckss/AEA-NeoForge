<script>
	import { base } from '$app/paths';
	import { nav, href } from '$lib/docs.js';

	let { current } = $props();
</script>

<nav aria-label="Pages">
	{#each nav as section}
		<div class="nav-section">
			<p class="nav-label">{section.label}</p>
			<ul class="nav-list">
				{#each section.pages as page}
					<li>
						<a href={href(base, page)} aria-current={page.slug === current ? 'page' : undefined}>{page.navTitle}</a>
					</li>
				{/each}
			</ul>
		</div>
	{/each}
</nav>
