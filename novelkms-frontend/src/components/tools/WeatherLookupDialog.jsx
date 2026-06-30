import { useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { toolsApi } from '../../api/tools'

const todayIso = () => new Date().toISOString().slice(0, 10)

const fmt = (value, unit) => {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  return `${Number(value).toFixed(1)}${unit ? ` ${unit}` : ''}`
}

const secondsToHours = (value) => {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  return `${(Number(value) / 3600).toFixed(1)} h`
}

export default function WeatherLookupDialog({ open, onClose }) {
  const [location, setLocation] = useState('')
  const [date, setDate] = useState(todayIso())
  const [units, setUnits] = useState('imperial')
  const [result, setResult] = useState(null)
  const [sceneContext, setSceneContext] = useState('')
  const [interpretation, setInterpretation] = useState(null)
  const [error, setError] = useState('')
  const [aiError, setAiError] = useState('')
  const [loading, setLoading] = useState(false)
  const [aiLoading, setAiLoading] = useState(false)

  const lookup = async () => {
    setLoading(true)
    setError('')
    setAiError('')
    setResult(null)
    setInterpretation(null)
    try {
      setResult(await toolsApi.weather({ location, date, units }))
    } catch (e) {
      setError(e.response?.data?.message || 'Could not look up weather.')
    } finally {
      setLoading(false)
    }
  }

  const explain = async () => {
    if (!result) return
    setAiLoading(true)
    setAiError('')
    setInterpretation(null)
    try {
      setInterpretation(await toolsApi.interpretWeather({ weather: result, sceneContext }))
    } catch (e) {
      setAiError(e.response?.data?.message || 'Could not generate the AI interpretation.')
    } finally {
      setAiLoading(false)
    }
  }

  const daily = result?.daily

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Weather lookup</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            Look up historical modeled weather or forecast weather for a scene date.
            AI interpretation is optional and is grounded in the returned weather facts.
          </Typography>

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField
              label="City / state / country"
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              fullWidth
              placeholder="Seoul, South Korea"
            />
            <TextField
              label="Date"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              InputLabelProps={{ shrink: true }}
              sx={{ minWidth: 180 }}
            />
            <FormControl sx={{ minWidth: 140 }}>
              <InputLabel id="weather-units-label">Units</InputLabel>
              <Select
                labelId="weather-units-label"
                label="Units"
                value={units}
                onChange={(e) => setUnits(e.target.value)}
              >
                <MenuItem value="imperial">Imperial</MenuItem>
                <MenuItem value="metric">Metric</MenuItem>
              </Select>
            </FormControl>
          </Stack>

          {error && <Alert severity="error">{error}</Alert>}

          {result && daily && (
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Stack spacing={1.5}>
                <Box>
                  <Typography variant="overline" color="text.secondary">
                    {result.dataKind === 'forecast' ? 'Forecast' : 'Historical modeled estimate'}
                  </Typography>
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>
                    {result.location?.displayName} · {daily.date}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {daily.weatherLabel} · {result.timezone}
                  </Typography>
                </Box>

                <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' }, gap: 1.5 }}>
                  <Metric label="High / low" value={`${fmt(daily.temperatureMax, result.temperatureUnit)} / ${fmt(daily.temperatureMin, result.temperatureUnit)}`} />
                  <Metric label="Feels like" value={`${fmt(daily.apparentTemperatureMax, result.temperatureUnit)} / ${fmt(daily.apparentTemperatureMin, result.temperatureUnit)}`} />
                  <Metric label="Precipitation" value={fmt(daily.precipitationSum, result.precipitationUnit)} />
                  <Metric label="Rain / snow" value={`${fmt(daily.rainSum, result.precipitationUnit)} / ${fmt(daily.snowfallSum, result.precipitationUnit)}`} />
                  <Metric label="Wind / gusts" value={`${fmt(daily.windSpeedMax, result.windSpeedUnit)} / ${fmt(daily.windGustsMax, result.windSpeedUnit)}`} />
                  <Metric label="Sunrise / sunset" value={`${daily.sunrise || '—'} / ${daily.sunset || '—'}`} />
                  <Metric label="Daylight" value={secondsToHours(daily.daylightDuration)} />
                  <Metric label="Sunshine" value={secondsToHours(daily.sunshineDuration)} />
                  <Metric label="Wind direction" value={daily.windDirectionDominant == null ? '—' : `${Math.round(daily.windDirectionDominant)}°`} />
                </Box>

                <Alert severity="info">{result.sourceNote}</Alert>

                <TextField
                  label="Scene context for AI interpretation (optional)"
                  value={sceneContext}
                  onChange={(e) => setSceneContext(e.target.value)}
                  multiline
                  minRows={2}
                  placeholder="Example: evening outside a field hospital; characters are tired and under stress."
                  fullWidth
                />

                {aiError && <Alert severity="error">{aiError}</Alert>}

                {interpretation && (
                  <Paper variant="outlined" sx={{ p: 1.5, bgcolor: 'background.default' }}>
                    <Typography variant="overline" color="text.secondary">
                      AI scene interpretation · {interpretation.promptVersion}
                    </Typography>
                    <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                      {interpretation.content}
                    </Typography>
                  </Paper>
                )}
              </Stack>
            </Paper>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        {result && (
          <Button onClick={explain} disabled={aiLoading}>
            {aiLoading ? 'Interpreting…' : 'Explain for scene writing'}
          </Button>
        )}
        <Button variant="contained" onClick={lookup} disabled={loading || !location || !date}>
          {loading ? 'Looking up…' : 'Look up weather'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function Metric({ label, value }) {
  return (
    <Box sx={{ p: 1.25, border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 650 }}>
        {value}
      </Typography>
    </Box>
  )
}
