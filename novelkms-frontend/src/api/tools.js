import client from './client'

export const toolsApi = {
  async dayOfWeek({ date, calendar }) {
    const { data } = await client.get('/tools/day-of-week', {
      params: { date, calendar },
    })
    return data
  },

  async weather({ location, date, units }) {
    const { data } = await client.get('/tools/weather', {
      params: { location, date, units },
    })
    return data
  },

  async interpretWeather({ weather, sceneContext }) {
    const { data } = await client.post('/tools/weather/interpret', { weather, sceneContext })
    return data
  },
}
