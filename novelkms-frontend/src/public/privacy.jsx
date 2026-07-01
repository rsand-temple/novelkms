import {
	Box,
	Button,
	Container,
	Divider,
	Link,
	Paper,
	Stack,
	Typography,
} from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { LogoLockup } from '../components/branding/Logo'

const CONTACT_EMAIL = 'support@novelkms.com'
const LAST_UPDATED = 'July 1, 2026'

function Section({ title, children }) {
	return (
		<Box>
			<Typography variant="h5" sx={{ fontWeight: 750, mb: 1.25 }}>
				{title}
			</Typography>
			<Stack spacing={1.25} sx={{ color: 'text.secondary' }}>
				{children}
			</Stack>
		</Box>
	)
}

function BulletList({ items }) {
	return (
		<Box component="ul" sx={{ mt: 0.25, mb: 0, pl: 3 }}>
			{items.map((item) => (
				<Typography component="li" color="text.secondary" key={item} sx={{ mb: 0.75 }}>
					{item}
				</Typography>
			))}
		</Box>
	)
}

export default function PrivacyPage() {
	return (
		<Box sx={{ minHeight: '100vh', bgcolor: 'background.paper', py: { xs: 3, md: 6 } }}>
			<Container maxWidth="md">
				<Paper elevation={0} sx={{ p: { xs: 2.5, md: 4 }, border: '1px solid', borderColor: 'divider' }}>
					<Stack spacing={4}>
						<Box>
							<LogoLockup width={300} />

							<Typography variant="h3" sx={{ mt: 3, fontWeight: 800, lineHeight: 1.1 }}>
								Privacy Policy
							</Typography>

							<Typography color="text.secondary" sx={{ mt: 1.5, fontSize: '1.05rem' }}>
								This policy explains what NovelKMS collects, how it is used, and how manuscript and account data
								are handled.
							</Typography>

							<Typography color="text.secondary" sx={{ mt: 1 }}>
								Last updated: {LAST_UPDATED}
							</Typography>

							<Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mt: 3 }}>
								<Button component={RouterLink} to="/login" variant="contained">
									Back to sign in
								</Button>
								<Button component={RouterLink} to="/faq" variant="outlined">
									Read the FAQ
								</Button>
							</Stack>
						</Box>

						<Divider />

						<Section title="Summary">
							<Typography color="text.secondary">
								NovelKMS is built for author control. We collect the information needed to provide accounts,
								authentication, subscriptions, manuscript management, AI-assisted review when you choose to use it,
								and basic support. We do not sell your personal information, and NovelKMS does not claim ownership of
								your manuscript.
							</Typography>
						</Section>

						<Section title="Information we collect">
							<Typography color="text.secondary">
								NovelKMS collects information you provide directly and information required to operate the service.
							</Typography>
							<BulletList
								items={[
									'Account information, such as your email address, display name, and authentication provider identity.',
									'Manuscript and project data you create, import, edit, organize, or store in NovelKMS.',
									'Codex entries, summaries, AI review artifacts, templates, settings, and related project metadata.',
									'Billing status and subscription identifiers synchronized from Stripe. NovelKMS does not store full card numbers.',
									'Operational data such as login/session state, timestamps, server logs, diagnostic errors, and security/audit records.',
								]}
							/>
						</Section>

						<Section title="OAuth sign-in">
							<Typography color="text.secondary">
								When you sign in with an external provider such as Google, Microsoft, or GitHub, NovelKMS receives the
								account information needed to create and recognize your NovelKMS account. This commonly includes your
								email address, name or display name, and the provider-specific account identifier. NovelKMS uses this
								information for authentication and account management.
							</Typography>
						</Section>

						<Section title="Manuscripts and project content">
							<Typography color="text.secondary">
								Your manuscript and project materials remain yours. NovelKMS stores this content so you can write,
								revise, organize, review, import, and export your work. NovelKMS does not claim ownership of your writing.
							</Typography>
							<Typography color="text.secondary">
								Because NovelKMS is a hosted application, manuscript and project data are stored on the NovelKMS server
								and in operational backups. Access to production data should be limited to operational, support,
								security, and maintenance needs.
							</Typography>
						</Section>

						<Section title="AI features and OpenAI keys">
							<Typography color="text.secondary">
								NovelKMS AI features are optional and use a bring-your-own-key model. If you add an OpenAI API key,
								NovelKMS stores it encrypted and shows only a masked key hint in the app.
							</Typography>
							<Typography color="text.secondary">
								NovelKMS sends manuscript excerpts, summaries, memory documents, guidance, and related project context
								to the configured AI provider only when you choose to run an AI generation or review workflow. AI output
								is stored in NovelKMS as review, summary, memory, or Codex-related project data.
							</Typography>
							<Typography color="text.secondary">
								Use of OpenAI is also governed by OpenAI's own terms and policies. You should review those policies
								before enabling AI features with your API key.
							</Typography>
						</Section>

						<Section title="Billing">
							<Typography color="text.secondary">
								NovelKMS uses Stripe for subscription checkout, billing management, invoices, payment methods, and
								cancellations. Stripe processes payment card details. NovelKMS stores local subscription and entitlement
								state, Stripe customer/subscription identifiers, billing event identifiers, and related subscription
								status information needed to provide access to the service.
							</Typography>
						</Section>

						<Section title="How we use information">
							<BulletList
								items={[
									'To create accounts, authenticate users, and protect access to user-owned projects.',
									'To provide manuscript editing, Codex, import/export, AI review, summaries, and related product features.',
									'To manage subscriptions, trials, cancellations, and access entitlements.',
									'To respond to support requests, diagnose bugs, improve reliability, and prevent abuse.',
									'To maintain backups, audit records, and operational security for the service.',
								]}
							/>
						</Section>

						<Section title="Sharing and third-party services">
							<Typography color="text.secondary">
								NovelKMS does not sell your personal information. We share information with third-party service
								providers only as needed to operate the service, such as authentication providers, Stripe for billing,
								OpenAI when you invoke AI features, hosting/infrastructure services, and backup or diagnostic tools.
							</Typography>
						</Section>

						<Section title="Cookies and sessions">
							<Typography color="text.secondary">
								NovelKMS may use cookies or similar browser storage for authentication sessions, security, preferences,
								and application functionality. These are used to keep you signed in and to operate the app.
							</Typography>
						</Section>

						<Section title="Security and backups">
							<Typography color="text.secondary">
								NovelKMS uses reasonable technical and operational safeguards for account and project data. Production
								data is stored in a database, and backups may contain manuscripts, project data, configuration, and
								secrets required for disaster recovery. No internet service can guarantee perfect security.
							</Typography>
						</Section>

						<Section title="Data export, cancellation, and deletion">
							<Typography color="text.secondary">
								NovelKMS is built around portability. You should export your manuscript and project materials before
								canceling if you no longer plan to use the service. If you want help deleting account or project data,
								contact NovelKMS support.
							</Typography>
						</Section>

						<Section title="Children">
							<Typography color="text.secondary">
								NovelKMS is intended for adults and is not directed to children under 13. Do not use NovelKMS if you are
								under 13.
							</Typography>
						</Section>

						<Section title="Changes to this policy">
							<Typography color="text.secondary">
								This policy may be updated as NovelKMS evolves. The updated version will be posted on this page with a
								new effective date.
							</Typography>
						</Section>

						<Section title="Contact">
							<Typography color="text.secondary">
								Questions about this Privacy Policy can be sent to{' '}
								<Link href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</Link>.
							</Typography>
						</Section>
					</Stack>
				</Paper>
			</Container>
		</Box>
	)
}
