#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const target = process.argv[2] || 'novelkms-frontend/src/components/layout/EditorPanel.jsx';
const file = path.resolve(process.cwd(), target);

if (!fs.existsSync(file)) {
  console.error(`Could not find ${file}`);
  console.error('Run this from the NovelKMS repository root, or pass the path to EditorPanel.jsx as the first argument.');
  process.exit(1);
}

let s = fs.readFileSync(file, 'utf8');

function replaceOnce(label, before, after) {
  if (s.includes(after)) {
    console.log(`${label}: already applied`);
    return;
  }
  const idx = s.indexOf(before);
  if (idx < 0) {
    console.error(`Could not find expected block for: ${label}`);
    process.exit(1);
  }
  s = s.slice(0, idx) + after + s.slice(idx + before.length);
  console.log(`${label}: applied`);
}

replaceOnce(
  'add editor lifecycle helpers',
`const AUTOSAVE_DELAY_MS = 1500;

// ── Review-rail resize ────────────────────────────────────────────────────────`,
`const AUTOSAVE_DELAY_MS = 1500;

const isEditorReady = (ed) =>
	Boolean(ed && !ed.isDestroyed && ed.view);

const runEditorCommand = (ed, fn) => {
	if (!isEditorReady(ed)) return false;
	fn(ed);
	return true;
};

// ── Review-rail resize ────────────────────────────────────────────────────────`
);

replaceOnce(
  'harden search decoration sync effect',
`// Keep the transient ProseMirror search decorations synchronized with the
	// shared search bar. Decorations never enter the stored HTML.
	useEffect(() => {
		if (!editor) return;
		if (!search.open || !search.query || templateMode || aiDocMode) {
			editor.commands.clearSearch();
			return;
		}
		// Search navigation must never change the manuscript selection. In
		// chapter mode the editor contains every scene in the chapter, so the
		// provider's flattened match index is already the correct document-wide
		// index. In single-scene mode it is likewise local to that scene.
		const editorMatchIndex = search.activeIndex >= 0 ? search.activeIndex : 0;
		editor.commands.setSearch({
			query: search.query,
			matchCase: search.matchCase,
			activeIndex: editorMatchIndex,
		});
		if (search.totalCount > 0) {
			requestAnimationFrame(() => editor.commands.scrollToSearchMatch(editorMatchIndex));
		}
	}, [editor, search.open, search.query, search.matchCase, search.activeIndex, search.totalCount, templateMode, aiDocMode]);`,
`// Keep the transient ProseMirror search decorations synchronized with the
	// shared search bar. Decorations never enter the stored HTML.
	useEffect(() => {
		if (!isEditorReady(editor)) return;
		if (!search.open || !search.query || templateMode || aiDocMode) {
			runEditorCommand(editor, ed => ed.commands.clearSearch());
			return;
		}
		// Search navigation must never change the manuscript selection. In
		// chapter mode the editor contains every scene in the chapter, so the
		// provider's flattened match index is already the correct document-wide
		// index. In single-scene mode it is likewise local to that scene.
		const editorMatchIndex = search.activeIndex >= 0 ? search.activeIndex : 0;
		const applied = runEditorCommand(editor, ed => ed.commands.setSearch({
			query: search.query,
			matchCase: search.matchCase,
			activeIndex: editorMatchIndex,
		}));
		if (applied && search.totalCount > 0) {
			requestAnimationFrame(() => {
				runEditorCommand(editor, ed => ed.commands.scrollToSearchMatch(editorMatchIndex));
			});
		}
	}, [editor, search.open, search.query, search.matchCase, search.activeIndex, search.totalCount, templateMode, aiDocMode]);`
);

replaceOnce(
  'harden registered search actions',
`useEffect(() => {
		if (!editor) return;

		registerEditorActions({
			next: () => editor.commands.goToNextSearchMatch(),
			previous: () => editor.commands.goToPreviousSearchMatch(),
			replaceCurrent: (replacement) =>
				editor.commands.replaceCurrentSearchMatch(replacement),
			replaceAll: (replacement) =>
				editor.commands.replaceAllSearchMatches(replacement),
		});

		return () => {
			registerEditorActions(null);
		};
	}, [editor, registerEditorActions]);`,
`useEffect(() => {
		if (!isEditorReady(editor)) return;

		registerEditorActions({
			next: () =>
				runEditorCommand(editor, ed => ed.commands.goToNextSearchMatch()),
			previous: () =>
				runEditorCommand(editor, ed => ed.commands.goToPreviousSearchMatch()),
			replaceCurrent: (replacement) =>
				runEditorCommand(editor, ed => ed.commands.replaceCurrentSearchMatch(replacement)),
			replaceAll: (replacement) =>
				runEditorCommand(editor, ed => ed.commands.replaceAllSearchMatches(replacement)),
		});

		return () => {
			registerEditorActions(null);
		};
	}, [editor, registerEditorActions]);`
);

replaceOnce(
  'harden scene break insertion',
`const handleSceneBreak = useCallback(async () => {
		const cid = chapterIdRef.current;
		const firstId = firstSceneIdRef.current;
		if (!cid || !firstId || !editorRef.current) return;
		try {
			const newScene = await scenesApi.create(cid, { title: '' });
			const ed = editorRef.current;
			ed.chain().focus().setSceneBreak({ sceneId: newScene.id }).run();`,
`const handleSceneBreak = useCallback(async () => {
		const cid = chapterIdRef.current;
		const firstId = firstSceneIdRef.current;
		if (!cid || !firstId || !isEditorReady(editorRef.current)) return;
		try {
			const newScene = await scenesApi.create(cid, { title: '' });
			const ed = editorRef.current;
			if (!isEditorReady(ed)) return;
			ed.chain().focus().setSceneBreak({ sceneId: newScene.id }).run();`
);

replaceOnce(
  'harden token insertion',
`const handleInsertToken = useCallback((token) => {
		editorRef.current?.chain().focus().insertTemplateToken({ token }).run();
	}, []);`,
`const handleInsertToken = useCallback((token) => {
		const ed = editorRef.current;
		if (!isEditorReady(ed)) return;
		ed.chain().focus().insertTemplateToken({ token }).run();
	}, []);`
);

replaceOnce(
  'harden preview html rendering',
`const previewHtml = useMemo(() => {
		if (!showEditorPreview || !editor) return '';
		return renderPreviewHtml(editor.getHTML(), previewValues);
	}, [showEditorPreview, previewValues, editor]);`,
`const previewHtml = useMemo(() => {
		if (!showEditorPreview || !isEditorReady(editor)) return '';
		return renderPreviewHtml(editor.getHTML(), previewValues);
	}, [showEditorPreview, previewValues, editor]);`
);

replaceOnce(
  'harden content loading effect',
`useEffect(() => {
		if (!editor) return;

		let cancelled = false;
		const setEditorContent = (html) => {
			queueMicrotask(() => {
				if (cancelled || editor.isDestroyed) return;
				editor.commands.setContent(html, false);
			});
			return () => {
				cancelled = true;
			};
		};`,
`useEffect(() => {
		if (!isEditorReady(editor)) return;

		let cancelled = false;
		const setEditorContent = (html) => {
			queueMicrotask(() => {
				runEditorCommand(editor, ed => {
					if (!cancelled) ed.commands.setContent(html, false);
				});
			});
			return () => {
				cancelled = true;
			};
		};`
);

replaceOnce(
  'harden codex generated body insertion',
`onBodyGenerated={(html) => editor?.commands.setContent(html, false)}`,
`onBodyGenerated={(html) => {
											runEditorCommand(editor, ed => ed.commands.setContent(html, false));
										}}`
);

fs.writeFileSync(file, s, 'utf8');
console.log(`Updated ${file}`);
