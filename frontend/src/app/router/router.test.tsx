import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { routes } from './routes.tsx'

describe('application router', () => {
  it('renders the SAEROI landing page at the root route', () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/'] })
    render(<RouterProvider router={router} />)

    expect(screen.getByRole('heading', { name: '지속 가능한 가치를 만드는 브랜드' })).toBeInTheDocument()
  })
})
