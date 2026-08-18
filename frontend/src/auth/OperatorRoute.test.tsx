import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthContext } from './authContextValue'
import { OperatorRoute } from './OperatorRoute'

describe('OperatorRoute', () => {
  it('does not reveal the dashboard to non-administrator users', () => {
    render(
      <AuthContext.Provider value={{
        ready: true,
        user: {
          id: 'viewer-1', username: 'viewer', email: 'viewer@example.test',
          organizationId: 'org-1', organizationRole: 'VIEWER', projects: [],
        },
        login: async () => undefined,
        logout: async () => undefined,
      }}>
        <MemoryRouter initialEntries={['/system-health']}>
          <Routes>
            <Route element={<OperatorRoute />}>
              <Route path="/system-health" element={<div>secret dashboard</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    )

    expect(screen.getByText('Operator access required')).toBeInTheDocument()
    expect(screen.queryByText('secret dashboard')).not.toBeInTheDocument()
  })
})
