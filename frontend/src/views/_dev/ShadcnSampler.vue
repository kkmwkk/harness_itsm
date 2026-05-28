<script setup lang="ts">
import { ref } from 'vue';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Table,
  TableHeader,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
} from '@/components/ui/table';

const rows = [
  { id: 'itg-ticket-v1-1', title: '샘플 페이지', status: 'DRAFT' },
  { id: 'itg-ticket-v1-2', title: '샘플 페이지', status: 'PUBLISHED' },
  { id: 'itg-ticket-v1-3', title: '샘플 페이지', status: 'DEPRECATED' },
];
const open = ref(false);
</script>

<template>
  <main class="mx-auto max-w-5xl p-6 space-y-10">
    <h1>Shadcn Sampler</h1>

    <section class="space-x-2">
      <Button>Default</Button>
      <Button variant="secondary">
        Secondary
      </Button>
      <Button variant="outline">
        Outline
      </Button>
      <Button variant="ghost">
        Ghost
      </Button>
      <Button variant="destructive">
        Destructive
      </Button>
      <Button variant="link">
        Link
      </Button>
    </section>

    <section class="max-w-sm">
      <Input placeholder="검색..." />
    </section>

    <section>
      <Card>
        <CardHeader>
          <CardTitle>샘플 카드</CardTitle>
        </CardHeader>
        <CardContent>본문 14px / 400. Hairline 보더만, 그림자 없음.</CardContent>
      </Card>
    </section>

    <section>
      <Dialog v-model:open="open">
        <DialogTrigger as-child>
          <Button variant="outline">
            Open dialog
          </Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>샘플 다이얼로그</DialogTitle>
          </DialogHeader>
          <p>모달 본문 — 14px / 400.</p>
        </DialogContent>
      </Dialog>
    </section>

    <section>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>id</TableHead>
            <TableHead>title</TableHead>
            <TableHead>status</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          <TableRow
            v-for="r in rows"
            :key="r.id"
          >
            <TableCell class="font-mono text-[13px]">
              {{ r.id }}
            </TableCell>
            <TableCell>{{ r.title }}</TableCell>
            <TableCell>{{ r.status }}</TableCell>
          </TableRow>
        </TableBody>
      </Table>
    </section>
  </main>
</template>
