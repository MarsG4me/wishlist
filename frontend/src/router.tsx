import { createBrowserRouter } from 'react-router-dom'
import { AppShell } from './components/layout/AppShell'
import { MyWishlistsPage } from './pages/MyWishlistsPage'
import { WishlistDetailPage } from './pages/WishlistDetailPage'
import { WishDetailPage } from './pages/WishDetailPage'
import { GroupsPage } from './pages/GroupsPage'
import { GroupDetailPage } from './pages/GroupDetailPage'
import { SharedWishlistPage } from './pages/SharedWishlistPage'
import { MyClaimsPage } from './pages/MyClaimsPage'
import { JoinGroupPage } from './pages/JoinGroupPage'
import { ProfilePage } from './pages/ProfilePage'
import { AdminPage } from './pages/AdminPage'

export const router = createBrowserRouter([
  {
    element: <AppShell />,
    children: [
      { path: '/', element: <MyWishlistsPage /> },
      { path: '/wishlists/:id', element: <WishlistDetailPage /> },
      { path: '/wishes/:id', element: <WishDetailPage /> },
      { path: '/groups', element: <GroupsPage /> },
      { path: '/groups/:id', element: <GroupDetailPage /> },
      { path: '/groups/:groupId/wishlists/:id', element: <SharedWishlistPage /> },
      { path: '/claims', element: <MyClaimsPage /> },
      { path: '/join/:code', element: <JoinGroupPage /> },
      { path: '/profile', element: <ProfilePage /> },
      { path: '/admin', element: <AdminPage /> },
    ],
  },
])
