import {
	Alert,
	Box,
	Button,
	CircularProgress,
	Dialog,
	DialogActions,
	DialogContent,
	DialogTitle,
	Stack,
	TextField,
	Typography,
} from '@mui/material'
import { useForm, ValidationError } from '@formspree/react'

const FORM_ID = 'xkolvkyk'

function FormspreeValidationError({ prefix, field, errors }) {
	return (
		<ValidationError
			prefix={prefix}
			field={field}
			errors={errors}
			style={{ color: '#b00020', fontSize: '0.8125rem', marginTop: 4 }}
		/>
	)
}

export default function ContactSupportDialog({ open, onClose }) {
	const [state, handleSubmit, reset] = useForm(FORM_ID)

	const handleClose = () => {
		reset()
		onClose()
	}

	return (
		<Dialog
			open={open}
			onClose={handleClose}
			fullWidth
			maxWidth="sm"
			aria-labelledby="contact-support-dialog-title"
		>
			<DialogTitle id="contact-support-dialog-title">
				Contact NovelKMS support
			</DialogTitle>

			{state.succeeded ? (
				<>
					<DialogContent dividers>
						<Stack spacing={2}>
							<Alert severity="success">
								Your message has been sent.
							</Alert>
							<Typography color="text.secondary">
								Thanks. A reply will go to the email address you provided.
							</Typography>
						</Stack>
					</DialogContent>
					<DialogActions>
						<Button onClick={handleClose}>Close</Button>
					</DialogActions>
				</>
			) : (
				<Box component="form" onSubmit={handleSubmit} noValidate>
					<DialogContent dividers>
						<Stack spacing={2.25}>
							<Typography color="text.secondary">
								Send a question, bug report, or feedback. Replies will come by email.
							</Typography>

							<Alert severity="info">
								Please do not include passwords, API keys, payment details, or manuscript text.
							</Alert>

							<input type="hidden" name="_subject" value="NovelKMS contact form" />
							<input
								type="text"
								name="_gotcha"
								tabIndex="-1"
								autoComplete="off"
								style={{ display: 'none' }}
							/>

							<Box>
								<TextField
									id="contact-support-email"
									label="Your email"
									name="email"
									type="email"
									required
									fullWidth
									autoComplete="email"
									disabled={state.submitting}
								/>
								<FormspreeValidationError prefix="Email" field="email" errors={state.errors} />
							</Box>

							<Box>
								<TextField
									id="contact-support-message"
									label="Message"
									name="message"
									required
									multiline
									minRows={6}
									fullWidth
									disabled={state.submitting}
								/>
								<FormspreeValidationError prefix="Message" field="message" errors={state.errors} />
							</Box>

							<FormspreeValidationError errors={state.errors} />
						</Stack>
					</DialogContent>

					<DialogActions>
						<Button onClick={handleClose} disabled={state.submitting}>
							Cancel
						</Button>
						<Button
							type="submit"
							variant="contained"
							disabled={state.submitting}
							startIcon={state.submitting ? <CircularProgress size={16} /> : null}
						>
							{state.submitting ? 'Sending…' : 'Send'}
						</Button>
					</DialogActions>
				</Box>
			)}
		</Dialog>
	)
}
