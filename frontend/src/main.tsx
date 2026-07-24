import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ThemeProvider, createTheme, CssBaseline } from '@mui/material'
import App from './App'
import { AuthProvider } from './auth/AuthContext'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
})

const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: { main: '#6366f1', light: '#818cf8', dark: '#4f46e5' },
    secondary: { main: '#22d3ee', light: '#67e8f9', dark: '#06b6d4' },
    success: { main: '#22c55e', light: '#4ade80', dark: '#16a34a' },
    warning: { main: '#f59e0b', light: '#fbbf24', dark: '#d97706' },
    error: { main: '#ef4444', light: '#f87171', dark: '#dc2626' },
    background: { default: '#0a0e1a', paper: '#111827' },
    text: { primary: '#e2e8f0', secondary: '#94a3b8' },
    divider: 'rgba(148, 163, 184, 0.08)',
  },
  typography: {
    fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
    h4: { fontWeight: 700, letterSpacing: '-0.02em' },
    h5: { fontWeight: 700, letterSpacing: '-0.01em' },
    h6: { fontWeight: 600 },
  },
  shape: { borderRadius: 12 },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          backgroundImage:
            'radial-gradient(ellipse at 20% 0%, rgba(99, 102, 241, 0.07) 0%, transparent 50%), ' +
            'radial-gradient(ellipse at 80% 100%, rgba(34, 211, 238, 0.04) 0%, transparent 50%)',
          backgroundAttachment: 'fixed',
        },
        '::-webkit-scrollbar': { width: 6, height: 6 },
        '::-webkit-scrollbar-track': { background: 'transparent' },
        '::-webkit-scrollbar-thumb': { background: 'rgba(148,163,184,0.18)', borderRadius: 3 },
        '::-webkit-scrollbar-thumb:hover': { background: 'rgba(148,163,184,0.28)' },
        // Browser autofill paints its own light/blue background + dark text, which is
        // unreadable on the dark theme. Repaint it to match the inputs with an inset shadow.
        // !important is required — Chrome applies its autofill box-shadow with higher priority,
        // so without it our override loses the cascade and the blue fill shows through.
        'input:-webkit-autofill, input:-webkit-autofill:hover, input:-webkit-autofill:focus, input:-webkit-autofill:active, textarea:-webkit-autofill, select:-webkit-autofill': {
          WebkitBoxShadow: '0 0 0 1000px #111827 inset !important',
          boxShadow: '0 0 0 1000px #111827 inset !important',
          WebkitTextFillColor: '#e2e8f0 !important',
          caretColor: '#e2e8f0 !important',
          borderRadius: 'inherit',
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
          background: 'rgba(17, 24, 39, 0.65)',
          backdropFilter: 'blur(12px)',
          border: '1px solid rgba(99, 102, 241, 0.08)',
          transition: 'all 0.2s ease-in-out',
          '&:hover': {
            border: '1px solid rgba(99, 102, 241, 0.18)',
            boxShadow: '0 8px 32px rgba(99, 102, 241, 0.08)',
          },
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
          background: 'rgba(17, 24, 39, 0.65)',
          backdropFilter: 'blur(12px)',
          border: '1px solid rgba(99, 102, 241, 0.08)',
        },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: { textTransform: 'none', fontWeight: 600 },
        contained: {
          background: 'linear-gradient(135deg, #6366f1 0%, #818cf8 100%)',
          boxShadow: '0 4px 14px rgba(99, 102, 241, 0.3)',
          '&:hover': {
            background: 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)',
            boxShadow: '0 6px 20px rgba(99, 102, 241, 0.4)',
          },
        },
        outlined: {
          borderColor: 'rgba(99, 102, 241, 0.3)',
          '&:hover': {
            borderColor: '#6366f1',
            background: 'rgba(99, 102, 241, 0.08)',
          },
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 500, borderRadius: 8 },
      },
    },
    MuiDialog: {
      styleOverrides: {
        paper: {
          backgroundImage: 'none',
          background: 'rgba(17, 24, 39, 0.95)',
          backdropFilter: 'blur(24px)',
          border: '1px solid rgba(99, 102, 241, 0.12)',
          boxShadow: '0 24px 64px rgba(0, 0, 0, 0.4)',
        },
      },
    },
    MuiTextField: {
      styleOverrides: {
        root: {
          '& .MuiOutlinedInput-root': {
            '& fieldset': { borderColor: 'rgba(148, 163, 184, 0.12)' },
            '&:hover fieldset': { borderColor: 'rgba(99, 102, 241, 0.3)' },
            '&.Mui-focused fieldset': { borderColor: '#6366f1' },
          },
        },
      },
    },
    MuiTabs: {
      styleOverrides: {
        indicator: {
          height: 3,
          borderRadius: '3px 3px 0 0',
          background: 'linear-gradient(90deg, #6366f1, #22d3ee)',
        },
      },
    },
    MuiTab: {
      styleOverrides: {
        root: { textTransform: 'none', fontWeight: 500, minHeight: 44 },
      },
    },
    MuiTableContainer: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
          background: 'rgba(17, 24, 39, 0.65)',
          backdropFilter: 'blur(12px)',
          border: '1px solid rgba(99, 102, 241, 0.08)',
          borderRadius: 12,
        },
      },
    },
    MuiTableHead: {
      styleOverrides: {
        root: {
          '& .MuiTableCell-head': {
            fontWeight: 600,
            color: '#94a3b8',
            borderBottomColor: 'rgba(148, 163, 184, 0.1)',
            fontSize: '0.75rem',
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
          },
        },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        root: { borderBottomColor: 'rgba(148, 163, 184, 0.06)' },
      },
    },
    MuiAlert: {
      styleOverrides: {
        root: { borderRadius: 12 },
      },
    },
    MuiSkeleton: {
      styleOverrides: {
        root: { background: 'rgba(148, 163, 184, 0.08)' },
      },
    },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          borderRadius: 10,
          '&.Mui-selected': {
            background: 'rgba(99, 102, 241, 0.12)',
            '&:hover': { background: 'rgba(99, 102, 241, 0.16)' },
          },
        },
      },
    },
    MuiMenu: {
      styleOverrides: {
        paper: {
          backgroundImage: 'none',
          background: 'rgba(17, 24, 39, 0.95)',
          backdropFilter: 'blur(24px)',
          border: '1px solid rgba(99, 102, 241, 0.12)',
        },
      },
    },
  },
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <BrowserRouter>
          <AuthProvider>
            <App />
          </AuthProvider>
        </BrowserRouter>
      </ThemeProvider>
    </QueryClientProvider>
  </React.StrictMode>
)
