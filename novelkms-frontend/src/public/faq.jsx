import {
	Accordion,
	AccordionDetails,
	AccordionSummary,
	Box,
	Button,
	Container,
	Divider,
	Paper,
	Stack,
	Typography,
} from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { Link as RouterLink } from 'react-router-dom'
import { LogoLockup } from '../components/branding/Logo'

const FAQ_SECTIONS = [
	{
		title: 'Your manuscript and data',
		items: [
			{
				question: 'Who owns my manuscript?',
				answer:
					'You do. NovelKMS is a workspace for organizing and revising your manuscript. It does not claim ownership of your writing.',
			},
			{
				question: 'Can I export my work?',
				answer:
					'Yes. NovelKMS is built around portability. DOCX import and export are part of the workflow, so your manuscript is not trapped inside the app.',
			},
			{
				question: 'What happens if I cancel?',
				answer:
					'You should export your manuscript and project materials before leaving the service. The goal is to make leaving possible without losing control of your work.',
			},
		],
	},
	{
		title: 'AI features',
		items: [
			{
				question: 'Does NovelKMS write my book for me?',
				answer:
					'No. NovelKMS uses AI for review, summaries, memory documents, and organization. It does not automatically rewrite your manuscript or insert AI prose into your book.',
			},
			{
				question: 'What does bring your own OpenAI key mean?',
				answer:
					'AI features use an OpenAI API key that you provide in your NovelKMS settings. This keeps AI usage under your control instead of bundling hidden AI credits into the subscription.',
			},
			{
				question: 'Can I use NovelKMS without AI?',
				answer:
					'Yes. The manuscript editor, project structure, Codex, import, export, and organization features are useful without enabling AI.',
			},
		],
	},
	{
		title: 'Billing',
		items: [
			{
				question: 'Is there a free trial?',
				answer:
					'Founders access currently includes a 14-day trial. After the trial, early subscribers can continue on the founders plan.',
			},
			{
				question: 'Can I cancel anytime?',
				answer:
					'Yes. Billing is managed through Stripe. You can manage or cancel your subscription from the Billing tab in NovelKMS settings.',
			},
			{
				question: 'What payment processor does NovelKMS use?',
				answer:
					'NovelKMS uses Stripe for checkout, subscriptions, invoices, payment methods, and cancellation management.',
			},
		],
	},
	{
		title: 'Product maturity',
		items: [
			{
				question: 'Is NovelKMS finished?',
				answer:
					'NovelKMS is actively developed. The core manuscript, AI review, summaries, Codex, authentication, billing, and administration foundations are in place, but the product will continue to evolve based on real author feedback.',
			},
			{
				question: 'Who is NovelKMS best for right now?',
				answer:
					'NovelKMS is best for serious long-form fiction authors who want manuscript structure, story knowledge, summaries, and AI-assisted review in one workspace.',
			},
		],
	},
	{
		title: 'Support',
		items: [
			{
				question: 'How do I report a bug or ask for help?',
				answer:
					'Use the support or contact channel provided in the app or on the NovelKMS website. Early users are especially encouraged to report rough edges and workflow friction.',
			},
			{
				question: 'Can I request features?',
				answer:
					'Yes. Early subscriber feedback will strongly influence the next development priorities.',
			},
		],
	},
]

function FaqSection({ section }) {
	return (
		<Box>
			<Typography variant="h5" sx={{ fontWeight: 750, mb: 1.5 }}>
				{section.title}
			</Typography>

			<Stack spacing={1}>
				{section.items.map((item) => (
					<Accordion key={item.question} disableGutters>
						<AccordionSummary expandIcon={<ExpandMoreIcon />}>
							<Typography sx={{ fontWeight: 650 }}>{item.question}</Typography>
						</AccordionSummary>
						<AccordionDetails>
							<Typography color="text.secondary">{item.answer}</Typography>
						</AccordionDetails>
					</Accordion>
				))}
			</Stack>
		</Box>
	)
}

export default function FaqPage() {
	return (
		<Box sx={{ minHeight: '100vh', bgcolor: 'background.paper', py: { xs: 3, md: 6 } }}>
			<Container maxWidth="md">
				<Paper elevation={0} sx={{ p: { xs: 2.5, md: 4 }, border: '1px solid', borderColor: 'divider' }}>
					<Stack spacing={4}>
						<Box>
							<LogoLockup width={300} />

							<Typography variant="h3" sx={{ mt: 3, fontWeight: 800, lineHeight: 1.1 }}>
								Frequently asked questions
							</Typography>

							<Typography color="text.secondary" sx={{ mt: 1.5, fontSize: '1.05rem' }}>
								Answers about manuscript ownership, AI features, subscriptions, export, and the current
								state of NovelKMS.
							</Typography>

							<Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mt: 3 }}>
								<Button component={RouterLink} to="/login" variant="contained">
									Back to sign in
								</Button>
								<Button component={RouterLink} to="/privacy" variant="outlined">
									Privacy Policy
								</Button>
							</Stack>
						</Box>

						<Divider />

						{FAQ_SECTIONS.map((section) => (
							<FaqSection key={section.title} section={section} />
						))}
					</Stack>
				</Paper>
			</Container>
		</Box>
	)
}