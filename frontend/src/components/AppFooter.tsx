import { Box, Typography } from '@mui/material'

/**
 * App-wide footer: "Monohull | v{version} | © {year} Ipqii Limited London".
 * Version is injected at build time from the Maven project version (see vite.config.ts);
 * the year is resolved at render time so it never goes stale.
 */
export default function AppFooter() {
  const year = new Date().getFullYear()

  const Sep = () => (
    <Box component="span" sx={{ mx: 1, color: '#334155' }}>|</Box>
  )

  return (
    <Box
      component="footer"
      sx={{
        mt: 4,
        pt: 2,
        borderTop: '1px solid rgba(148, 163, 184, 0.06)',
        textAlign: 'center',
      }}
    >
      <Typography
        variant="caption"
        sx={{ color: '#64748b', fontSize: '0.7rem', letterSpacing: '0.02em' }}
      >
        <Box component="span" sx={{ fontWeight: 700, color: '#94a3b8' }}>Monohull</Box>
        <Sep />
        v{__APP_VERSION__}
        <Sep />
        © {year} Ipqii Limited London
      </Typography>
    </Box>
  )
}
