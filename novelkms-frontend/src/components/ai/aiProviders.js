// Single source of truth for the AI providers NovelKMS supports.
//
// Both the credentials editor (AiCredentialsPanel) and the per-document provider
// selector (AiDocProviderSelect) read this roster so provider keys and their
// friendly labels never drift apart. A provider "key" is the exact string stored
// on ai_credential.provider and on every per-provider AI document
// (chapter_memory / chapter_summary / chapter_editorial / book_summary), e.g.
// 'OPENAI'.

export const AI_PROVIDERS = [
	{
		key: 'OPENAI',
		label: 'OpenAI',
		keyPrefix: 'sk-…',
		modelDefault: 'gpt-5.4',
		modelHelper: 'e.g. gpt-5.4, o3, gpt-4o',
		keyHelper: 'Stored encrypted; shown only as ••••last4 afterward.',
	},
	{
		key: 'ANTHROPIC',
		label: 'Anthropic (Claude)',
		keyPrefix: 'sk-ant-…',
		modelDefault: 'claude-sonnet-4-6',
		modelHelper: 'e.g. claude-sonnet-4-6, claude-opus-4-6, claude-haiku-4-5',
		keyHelper: 'Stored encrypted; shown only as ••••last4 afterward.',
	},
	{
		key: 'GEMINI',
		label: 'Google Gemini',
		keyPrefix: 'AIza…',
		modelDefault: 'gemini-2.5-flash',
		modelHelper: 'e.g. gemini-2.5-flash, gemini-2.0-flash, gemini-1.5-pro',
		keyHelper: 'Stored encrypted; shown only as ••••last4 afterward.',
	},
]

export const AI_PROVIDER_MAP = Object.fromEntries(AI_PROVIDERS.map(p => [p.key, p]))

// Friendly label for a provider key. Unknown/removed keys (e.g. a legacy variant
// whose provider is no longer in the roster) fall back to the raw key so the UI
// still shows something meaningful rather than nothing.
export function providerLabel(key) {
	if (!key) return ''
	return AI_PROVIDER_MAP[key]?.label ?? key
}
