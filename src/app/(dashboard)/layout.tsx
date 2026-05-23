import Link from 'next/link';

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-screen bg-canvas">
      <nav className="h-11 bg-surface-black">
        <div className="mx-auto flex h-full max-w-[1440px] items-center px-6">
          <Link
            href="/"
            className="text-[14px] leading-[1.29] tracking-[-0.224px] text-body-on-dark"
          >
            TubeNote
          </Link>
        </div>
      </nav>
      <main className="mx-auto max-w-[1440px] px-6 py-12 md:py-16">
        {children}
      </main>
    </div>
  );
}
