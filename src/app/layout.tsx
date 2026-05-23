import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "TubeNote",
  description: "유튜브 링크 하나로 영상을 요약하는 1인용 대시보드.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
