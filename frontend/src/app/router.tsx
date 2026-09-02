import { createBrowserRouter, type RouteObject } from 'react-router-dom'
import { ShopProvider } from '../features/shop/ShopContext.tsx'
import { LandingPage } from '../pages/landing/LandingPage.tsx'

const landingPageElement = (
  <ShopProvider>
    <LandingPage />
  </ShopProvider>
)

export const routes: RouteObject[] = [
  { path: '/', element: landingPageElement },
  { path: '*', element: landingPageElement },
]

export const router = createBrowserRouter(routes)
