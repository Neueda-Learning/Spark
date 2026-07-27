import { NavLink, Outlet } from 'react-router-dom';

function Layout() {
  const linkClass = ({ isActive }: { isActive: boolean }) =>
    [
      'px-4 py-2 rounded-md text-sm font-medium transition-colors',
      isActive
        ? 'bg-slate-700 text-white'
        : 'text-slate-300 hover:bg-slate-800 hover:text-white',
    ].join(' ');

  return (
    <div className="min-h-screen bg-slate-50">
      <nav className="bg-slate-900 shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between h-16">
            <span className="text-white text-xl font-bold tracking-tight">
              Portfolio Manager
            </span>
            <div className="flex space-x-2">
              <NavLink to="/overview" className={linkClass}>
                组合总览
              </NavLink>
              <NavLink to="/investments" className={linkClass}>
                投资操作
              </NavLink>
            </div>
          </div>
        </div>
      </nav>
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <Outlet />
      </main>
    </div>
  );
}

export default Layout;
