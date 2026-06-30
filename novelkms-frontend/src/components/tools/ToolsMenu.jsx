import { useState } from 'react'
import { Button, Menu, MenuItem } from '@mui/material'
import ArrowDropDownIcon from '@mui/icons-material/ArrowDropDown'
import BuildOutlinedIcon from '@mui/icons-material/BuildOutlined'
import CalendarMonthOutlinedIcon from '@mui/icons-material/CalendarMonthOutlined'
import WbSunnyOutlinedIcon from '@mui/icons-material/WbSunnyOutlined'
import DayOfWeekDialog from './DayOfWeekDialog'
import WeatherLookupDialog from './WeatherLookupDialog'

export default function ToolsMenu({ buttonSx }) {
  const [anchor, setAnchor] = useState(null)
  const [dayOpen, setDayOpen] = useState(false)
  const [weatherOpen, setWeatherOpen] = useState(false)

  const openDay = () => {
    setAnchor(null)
    setDayOpen(true)
  }

  const openWeather = () => {
    setAnchor(null)
    setWeatherOpen(true)
  }

  return (
    <>
      <Button
        color="inherit"
        size="small"
        startIcon={<BuildOutlinedIcon fontSize="small" />}
        endIcon={<ArrowDropDownIcon />}
        onClick={(e) => setAnchor(e.currentTarget)}
        sx={buttonSx}
      >
        Tools
      </Button>
      <Menu anchorEl={anchor} open={!!anchor} onClose={() => setAnchor(null)}>
        <MenuItem onClick={openDay}>
          <CalendarMonthOutlinedIcon fontSize="small" sx={{ mr: 1.25 }} />
          Day-of-week calculator…
        </MenuItem>
        <MenuItem onClick={openWeather}>
          <WbSunnyOutlinedIcon fontSize="small" sx={{ mr: 1.25 }} />
          Weather lookup…
        </MenuItem>
      </Menu>

      <DayOfWeekDialog open={dayOpen} onClose={() => setDayOpen(false)} />
      <WeatherLookupDialog open={weatherOpen} onClose={() => setWeatherOpen(false)} />
    </>
  )
}
