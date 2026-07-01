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
const LEGAL_ENTITY = 'NovelKMS, LLC'
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

export default function TermsPage() {
	return (
		<Box sx={{ minHeight: '100vh', bgcolor: 'background.paper', py: { xs: 3, md: 6 } }}>
			<Container maxWidth="md">
				<Paper elevation={0} sx={{ p: { xs: 2.5, md: 4 }, border: '1px solid', borderColor: 'divider' }}>
					<Stack spacing={4}>
						<Box>
							<LogoLockup width={300} />

							<Typography variant="h3" sx={{ mt: 3, fontWeight: 800, lineHeight: 1.1 }}>
								Terms of Service
							</Typography>

							<Typography color="text.secondary" sx={{ mt: 1.5, fontSize: '1.05rem' }}>
								These Terms govern your access to and use of NovelKMS, including manuscript management,
								project knowledge-management features, AI-assisted review workflows, subscriptions, exports,
								and related services.
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
								<Button component={RouterLink} to="/privacy" variant="outlined">
									Privacy Policy
								</Button>
							</Stack>
						</Box>

						<Divider />

						<Section title="1. Agreement to these Terms">
							<Typography color="text.secondary">
								NovelKMS is operated by {LEGAL_ENTITY}. By creating an account, signing in, starting a trial,
								subscribing, or using NovelKMS, you agree to these Terms. If you do not agree, do not use the
								service.
							</Typography>
							<Typography color="text.secondary">
								If you use NovelKMS on behalf of an organization, you represent that you have authority to bind
								that organization to these Terms.
							</Typography>
						</Section>

						<Section title="2. The NovelKMS service">
							<Typography color="text.secondary">
								NovelKMS is a hosted authoring and knowledge-management workspace for long-form fiction. It
								includes manuscript structure, rich text editing, Codex/project knowledge, summaries, AI review
								artifacts, import/export tools, account settings, billing features, and related functionality.
							</Typography>
							<Typography color="text.secondary">
								NovelKMS may change over time. Features may be added, changed, limited, suspended, or removed as
								the service evolves.
							</Typography>
						</Section>

						<Section title="3. Accounts and security">
							<Typography color="text.secondary">
								You are responsible for maintaining control of the accounts and sign-in providers you use to
								access NovelKMS. You are responsible for activity under your NovelKMS account unless caused by
								NovelKMS&apos;s own security failure.
							</Typography>
							<BulletList
								items={[
									'Provide accurate account information and keep it current.',
									'Do not share your account access with unauthorized users.',
									'Notify NovelKMS promptly if you believe your account has been compromised.',
									'Use NovelKMS only in compliance with applicable laws and these Terms.',
								]}
							/>
						</Section>

						<Section title="4. Your manuscripts and project content">
							<Typography color="text.secondary">
								You retain ownership of the manuscripts, notes, Codex entries, characters, canon, timelines,
								research, summaries, templates, images, and other content you create, import, or store in
								NovelKMS. NovelKMS does not claim ownership of your writing.
							</Typography>
							<Typography color="text.secondary">
								You grant {LEGAL_ENTITY} a limited license to host, store, process, display, back up, export,
								transmit, and otherwise use your content only as needed to provide, maintain, secure, support,
								and improve NovelKMS and to perform actions you request through the service.
							</Typography>
							<Typography color="text.secondary">
								You are responsible for ensuring that you have the rights needed to upload, store, process, and
								use your content in NovelKMS.
							</Typography>
						</Section>

						<Section title="5. AI-assisted features">
							<Typography color="text.secondary">
								NovelKMS may include optional AI-assisted workflows, such as manuscript review, chapter or book
								summaries, memory documents, and Codex-related recommendations. These features are intended to
								assist your editorial workflow, not to replace your judgment.
							</Typography>
							<Typography color="text.secondary">
								NovelKMS currently uses a bring-your-own-key model for supported AI providers. If you configure
								an AI provider key, you are responsible for your provider account, provider charges, provider
								terms, and provider policies.
							</Typography>
							<Typography color="text.secondary">
								When you choose to run an AI workflow, NovelKMS may send manuscript excerpts, summaries, memory
								documents, user guidance, and related project context to the configured AI provider. NovelKMS
								does not send manuscript content to an AI provider unless you invoke an AI feature that requires
								it.
							</Typography>
							<Typography color="text.secondary">
								AI output may be inaccurate, incomplete, repetitive, inappropriate, or unsuitable for your work.
								You are responsible for reviewing and deciding whether to use, ignore, revise, or delete AI output.
							</Typography>
						</Section>

						<Section title="6. Subscriptions, trials, and billing">
							<Typography color="text.secondary">
								NovelKMS may offer trials, paid subscriptions, complimentary access, or other access plans.
								Subscription checkout, payment methods, invoices, renewals, cancellations, and billing management
								may be handled through Stripe or another third-party payment processor.
							</Typography>
							<Typography color="text.secondary">
								Unless otherwise stated at checkout, paid subscriptions renew automatically until canceled. You
								can manage or cancel your subscription through the Billing area in NovelKMS or through the
								billing portal made available by the service.
							</Typography>
							<Typography color="text.secondary">
								If you cancel a paid subscription, access may continue until the end of the then-current billing
								period unless otherwise stated. If payment fails or a subscription becomes inactive, NovelKMS may
								limit or suspend access to paid features.
							</Typography>
							<Typography color="text.secondary">
								Except where required by law or expressly stated in writing, fees are non-refundable. NovelKMS may
								change prices or plan terms prospectively with reasonable notice where required.
							</Typography>
						</Section>

						<Section title="7. Data export, cancellation, and deletion">
							<Typography color="text.secondary">
								NovelKMS is built around portability. You should export manuscripts and project materials before
								canceling if you no longer plan to use the service.
							</Typography>
							<Typography color="text.secondary">
								If you want help deleting account or project data, contact NovelKMS support at{' '}
								<Link href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</Link>. Some data may remain for a limited
								time in backups, logs, audit records, billing records, or other operational systems where retained
								for security, legal, accounting, fraud-prevention, or disaster-recovery purposes.
							</Typography>
						</Section>

						<Section title="8. Acceptable use">
							<Typography color="text.secondary">
								You may not misuse NovelKMS or interfere with the service, other users, or the underlying
								infrastructure.
							</Typography>
							<BulletList
								items={[
									'Do not attempt to gain unauthorized access to NovelKMS, other accounts, servers, databases, or networks.',
									'Do not probe, scan, attack, disrupt, overload, reverse engineer, scrape, or bypass security or access controls.',
									'Do not upload malware, malicious scripts, or content intended to damage systems or compromise accounts.',
									'Do not use NovelKMS to violate intellectual-property rights, privacy rights, publicity rights, or applicable law.',
									'Do not resell, sublicense, or commercially exploit access to NovelKMS unless NovelKMS expressly permits it in writing.',
									'Do not misrepresent your identity, affiliation, or authorization to use an account or organization.',
								]}
							/>
						</Section>

						<Section title="9. Service availability and backups">
							<Typography color="text.secondary">
								NovelKMS uses reasonable efforts to keep the service available and to protect user data, but no
								internet service can guarantee uninterrupted availability, perfect security, or complete immunity
								from data loss.
							</Typography>
							<Typography color="text.secondary">
								You are encouraged to maintain your own exports and backups of important manuscript and project
								materials. NovelKMS backup systems are intended for operational recovery and are not a substitute
								for your own retained copies.
							</Typography>
						</Section>

						<Section title="10. Third-party services">
							<Typography color="text.secondary">
								NovelKMS may rely on third-party services for authentication, payment processing, hosting,
								backups, diagnostics, AI processing, email, or related operations. Your use of those services may
								also be governed by their own terms, policies, and account agreements.
							</Typography>
							<Typography color="text.secondary">
								NovelKMS is not responsible for third-party services that it does not control.
							</Typography>
						</Section>

						<Section title="11. Intellectual property in NovelKMS">
							<Typography color="text.secondary">
								NovelKMS, including its software, design, branding, workflows, documentation, and service
								interfaces, is owned by {LEGAL_ENTITY} or its licensors. These Terms do not transfer ownership of
								NovelKMS to you.
							</Typography>
							<Typography color="text.secondary">
								Subject to these Terms, NovelKMS grants you a limited, revocable, non-exclusive,
								non-transferable right to access and use the service for your own authoring and project-management
								purposes.
							</Typography>
						</Section>

						<Section title="12. Feedback">
							<Typography color="text.secondary">
								If you send suggestions, bug reports, feature requests, or other feedback, you permit NovelKMS to
								use that feedback without restriction or compensation. This does not give NovelKMS ownership of
								your manuscripts or project content.
							</Typography>
						</Section>

						<Section title="13. Suspension and termination">
							<Typography color="text.secondary">
								NovelKMS may suspend or terminate access if you violate these Terms, create security or legal
								risk, fail to pay required fees, misuse the service, or if continued access would harm NovelKMS,
								other users, or third-party providers.
							</Typography>
							<Typography color="text.secondary">
								You may stop using NovelKMS at any time. Cancellation or termination may not automatically delete
								all account, billing, audit, backup, or operational records.
							</Typography>
						</Section>

						<Section title="14. Disclaimers">
							<Typography color="text.secondary">
								NovelKMS is provided on an “as is” and “as available” basis. To the fullest extent permitted by
								law, {LEGAL_ENTITY} disclaims all warranties, whether express, implied, statutory, or otherwise,
								including implied warranties of merchantability, fitness for a particular purpose, title, and
								non-infringement.
							</Typography>
							<Typography color="text.secondary">
								NovelKMS does not guarantee that the service will be uninterrupted, error-free, secure, or free
								from data loss. NovelKMS does not guarantee publication, representation, sales, editorial quality,
								continuity accuracy, marketability, copyright clearance, or the accuracy or usefulness of AI output.
							</Typography>
						</Section>

						<Section title="15. Limitation of liability">
							<Typography color="text.secondary">
								To the fullest extent permitted by law, {LEGAL_ENTITY} will not be liable for indirect,
								incidental, special, consequential, exemplary, or punitive damages, or for lost profits, lost
								revenue, lost business opportunities, loss of goodwill, loss of data, manuscript loss, publication
								delays, or publishing outcomes.
							</Typography>
							<Typography color="text.secondary">
								To the fullest extent permitted by law, {LEGAL_ENTITY}&apos;s total liability for all claims
								relating to NovelKMS will not exceed the amount you paid to NovelKMS for the service during the
								three months before the event giving rise to the claim, or fifty U.S. dollars, whichever is greater.
							</Typography>
						</Section>

						<Section title="16. Indemnification">
							<Typography color="text.secondary">
								You agree to defend, indemnify, and hold harmless {LEGAL_ENTITY} from claims, damages, liabilities,
								costs, and expenses arising from your content, your use of NovelKMS, your violation of these Terms,
								your violation of law, or your infringement or alleged infringement of another person&apos;s rights.
							</Typography>
						</Section>

						<Section title="17. Governing law">
							<Typography color="text.secondary">
								These Terms are governed by the laws of the Commonwealth of Pennsylvania, without
								regard to conflict-of-law rules, unless applicable law requires otherwise. Any dispute will be
								handled in the courts located in that jurisdiction unless a different venue is required by
								applicable law.
							</Typography>
							<Typography color="text.secondary">
								This section should be reviewed and finalized based on NovelKMS, LLC&apos;s actual state of
								organization and preferred dispute-resolution approach.
							</Typography>
						</Section>

						<Section title="18. Changes to these Terms">
							<Typography color="text.secondary">
								NovelKMS may update these Terms as the service evolves. The updated version will be posted on this
								page with a new effective date. Continued use of NovelKMS after updated Terms become effective
								means you accept the updated Terms.
							</Typography>
						</Section>

						<Section title="19. Contact">
							<Typography color="text.secondary">
								Questions about these Terms can be sent to {LEGAL_ENTITY} at{' '}
								<Link href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</Link>.
							</Typography>
						</Section>

						<Divider />

						<Typography color="text.secondary" sx={{ fontSize: '0.875rem' }}>
							Copyright © 2026 {LEGAL_ENTITY}. All rights reserved.
						</Typography>
					</Stack>
				</Paper>
			</Container>
		</Box>
	)
}