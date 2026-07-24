import { useState } from 'react'
import { Routes, Route, Navigate, Link, useLocation } from 'react-router-dom'
import {
  Box, Drawer, List, ListItem, ListItemButton, ListItemIcon, ListItemText,
  Typography, IconButton, AppBar, Toolbar, useMediaQuery, useTheme,
  Button, Skeleton,
} from '@mui/material'
import DashboardIcon from '@mui/icons-material/DashboardRounded'
import StorageIcon from '@mui/icons-material/DnsRounded'
import BoltIcon from '@mui/icons-material/BoltRounded'
import AccountTreeIcon from '@mui/icons-material/AccountTreeRounded'
import VpnKeyIcon from '@mui/icons-material/VpnKeyRounded'
import CodeIcon from '@mui/icons-material/CodeRounded'
import RocketLaunchIcon from '@mui/icons-material/RocketLaunchRounded'
import LogoutIcon from '@mui/icons-material/LogoutRounded'
import MenuIcon from '@mui/icons-material/MenuRounded'
import DashboardPage from './pages/DashboardPage'
import EnvironmentDetailPage from './pages/EnvironmentDetailPage'
import ImageConfigPage from './pages/ImageConfigPage'
import EnvironmentConfigEditPage from './pages/EnvironmentConfigEditPage'
import ActionsConfigPage from './pages/ActionsConfigPage'
import ActionEditPage from './pages/ActionEditPage'
import PipelinesPage from './pages/PipelinesPage'
import RegistryConfigPage from './pages/RegistryConfigPage'
import RepositoriesConfigPage from './pages/RepositoriesConfigPage'
import LoginPage from './pages/LoginPage'
import AppFooter from './components/AppFooter'
import { useAuth } from './auth/AuthContext'

const DRAWER_WIDTH = 260

const navItems = [
  { label: 'Dashboard', icon: <DashboardIcon />, path: '/' },
  { label: 'Environments', icon: <StorageIcon />, path: '/config/environments' },
  { label: 'Actions', icon: <BoltIcon />, path: '/config/actions' },
  { label: 'Pipelines', icon: <AccountTreeIcon />, path: '/pipelines' },
  { label: 'Repositories', icon: <CodeIcon />, path: '/config/repositories' },
  { label: 'Registry', icon: <VpnKeyIcon />, path: '/config/registry' },
]

function SidebarContent({ onClose }: { onClose?: () => void }) {
  const location = useLocation()

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Brand */}
      <Box sx={{ p: 2.5, pb: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box
            sx={{
              width: 38, height: 38, borderRadius: 2.5,
              background: 'linear-gradient(135deg, #6366f1 0%, #22d3ee 100%)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: '0 4px 14px rgba(99, 102, 241, 0.3)',
            }}
          >
            <RocketLaunchIcon sx={{ color: '#fff', fontSize: 20 }} />
          </Box>
          <Box>
            <Typography variant="h6" fontWeight={800} sx={{ lineHeight: 1.2, letterSpacing: '-0.02em' }}>
              Monohull
            </Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary', fontSize: '0.65rem', letterSpacing: '0.02em' }}>
              Dev Environment Manager
            </Typography>
          </Box>
        </Box>
      </Box>

      {/* Divider */}
      <Box sx={{ mx: 2.5, borderBottom: '1px solid rgba(148, 163, 184, 0.06)' }} />

      {/* Navigation */}
      <List sx={{ px: 1.5, py: 2, flex: 1 }}>
        {navItems.map(item => {
          const active = item.path === '/'
            ? location.pathname === '/'
            : location.pathname.startsWith(item.path)
          return (
            <ListItem key={item.path} disablePadding sx={{ mb: 0.5 }}>
              <ListItemButton
                component={Link}
                to={item.path}
                selected={active}
                onClick={onClose}
                sx={{
                  py: 1.2,
                  px: 1.5,
                  borderRadius: 2.5,
                  transition: 'all 0.15s ease',
                  ...(active && {
                    background: 'rgba(99, 102, 241, 0.1)',
                    '& .MuiListItemIcon-root': { color: '#818cf8' },
                    '& .MuiListItemText-primary': { color: '#e2e8f0', fontWeight: 600 },
                  }),
                  ...(!active && {
                    '& .MuiListItemIcon-root': { color: '#64748b' },
                    '& .MuiListItemText-primary': { color: '#94a3b8' },
                    '&:hover': {
                      background: 'rgba(148, 163, 184, 0.06)',
                      '& .MuiListItemIcon-root': { color: '#94a3b8' },
                    },
                  }),
                }}
              >
                <ListItemIcon sx={{ minWidth: 38 }}>{item.icon}</ListItemIcon>
                <ListItemText
                  primary={item.label}
                  primaryTypographyProps={{ fontSize: '0.9rem' }}
                />
              </ListItemButton>
            </ListItem>
          )
        })}
      </List>

      {/* Footer */}
      <Box sx={{ p: 2.5, pt: 0 }}>
        <UserFooter />
      </Box>
    </Box>
  )
}

function UserFooter() {
  const { user, logout } = useAuth()
  return (
    <Box
      sx={{
        p: 2,
        borderRadius: 3,
        background: 'rgba(99, 102, 241, 0.06)',
        border: '1px solid rgba(99, 102, 241, 0.08)',
      }}
    >
      {user && (
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="caption" sx={{ color: '#64748b', fontSize: '0.6rem', display: 'block' }}>
              Signed in as
            </Typography>
            <Typography
              variant="body2"
              sx={{ color: '#e2e8f0', fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
            >
              {user.username}
            </Typography>
          </Box>
          <IconButton
            size="small"
            onClick={() => { void logout() }}
            title="Sign out"
            sx={{ color: '#94a3b8', '&:hover': { color: '#f87171' } }}
          >
            <LogoutIcon fontSize="small" />
          </IconButton>
        </Box>
      )}
      <Typography variant="caption" sx={{ color: '#64748b', fontSize: '0.65rem' }}>
        Maximo Automated Development Environment
      </Typography>
    </Box>
  )
}

/** Redirects to /login unless an authenticated session exists. */
function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return (
      <Box sx={{ p: 4, maxWidth: 1200, mx: 'auto' }}>
        <Skeleton variant="text" width={240} height={48} sx={{ mb: 2 }} />
        <Skeleton variant="rounded" height={140} sx={{ mb: 2 }} />
        <Skeleton variant="rounded" height={140} />
      </Box>
    )
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }
  return <>{children}</>
}

function MainLayout() {
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down('md'))
  const [mobileOpen, setMobileOpen] = useState(false)

  const drawerPaperSx = {
    width: DRAWER_WIDTH,
    backgroundImage: 'none',
    background: 'rgba(10, 14, 26, 0.85)',
    backdropFilter: 'blur(20px)',
    borderRight: '1px solid rgba(148, 163, 184, 0.06)',
  }

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      {/* Mobile top bar */}
      {isMobile && (
        <AppBar
          position="fixed"
          sx={{
            backgroundImage: 'none',
            background: 'rgba(10, 14, 26, 0.85)',
            backdropFilter: 'blur(20px)',
            borderBottom: '1px solid rgba(148, 163, 184, 0.06)',
            boxShadow: 'none',
          }}
        >
          <Toolbar>
            <IconButton edge="start" onClick={() => setMobileOpen(true)} sx={{ mr: 1, color: '#94a3b8' }}>
              <MenuIcon />
            </IconButton>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <RocketLaunchIcon sx={{ color: '#6366f1', fontSize: 20 }} />
              <Typography variant="h6" fontWeight={700} sx={{ fontSize: '1rem' }}>Monohull</Typography>
            </Box>
          </Toolbar>
        </AppBar>
      )}

      {/* Sidebar */}
      <Box component="nav" sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}>
        {isMobile ? (
          <Drawer
            variant="temporary"
            open={mobileOpen}
            onClose={() => setMobileOpen(false)}
            ModalProps={{ keepMounted: true }}
            sx={{ '& .MuiDrawer-paper': drawerPaperSx }}
          >
            <SidebarContent onClose={() => setMobileOpen(false)} />
          </Drawer>
        ) : (
          <Drawer
            variant="permanent"
            open
            sx={{ '& .MuiDrawer-paper': drawerPaperSx }}
          >
            <SidebarContent />
          </Drawer>
        )}
      </Box>

      {/* Main content */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          // Without minWidth:0 a flex child won't shrink below its content's intrinsic
          // width, so wide/nowrap content (e.g. the Pipelines panels) blows the page past
          // the viewport on mobile and pushes Select menus off-screen.
          minWidth: 0,
          display: 'flex',
          flexDirection: 'column',
          p: { xs: 2, sm: 3, md: 4 },
          mt: { xs: 8, md: 0 },
          maxWidth: { md: `calc(100vw - ${DRAWER_WIDTH}px)` },
          minHeight: '100vh',
        }}
      >
        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Routes>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/environments/:id" element={<EnvironmentDetailPage />} />
            <Route path="/config/environments" element={<ImageConfigPage />} />
            <Route path="/config/environments/new" element={<EnvironmentConfigEditPage />} />
            <Route path="/config/environments/:id/edit" element={<EnvironmentConfigEditPage />} />
            <Route path="/config/images" element={<Navigate to="/config/environments" replace />} />
            <Route path="/config/actions" element={<ActionsConfigPage />} />
            <Route path="/config/actions/new" element={<ActionEditPage />} />
            <Route path="/config/actions/:id/edit" element={<ActionEditPage />} />
            <Route path="/pipelines" element={<PipelinesPage />} />
            <Route path="/config/repositories" element={<RepositoriesConfigPage />} />
            <Route path="/config/registry" element={<RegistryConfigPage />} />
          </Routes>
        </Box>
        <AppFooter />
      </Box>
    </Box>
  )
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/*"
        element={
          <RequireAuth>
            <MainLayout />
          </RequireAuth>
        }
      />
    </Routes>
  )
}

export default App
