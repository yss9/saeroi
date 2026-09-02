export interface Brand {
  id: string
  name: string
  description: string
  image: string
  imageAlt: string
}

export interface Product {
  id: string
  name: string
  brand: string
  price: string
  image: string
  imagePosition: string
}

export interface TrustItem {
  id: 'verified-source' | 'material-journey' | 'qr-tracking'
  title: string
  description: string
}

export interface FooterLink {
  label: string
  href: string
}

export interface FooterNavigationGroup {
  title: string
  links: FooterLink[]
}

export const brands: Brand[] = [
  {
    id: 'recode',
    name: 'RE:CODE',
    description: '버려지는 옷에 새로운 쓰임을 더합니다.',
    image: '/images/landing/brand-recode.webp',
    imageAlt: 'RE:CODE 로고',
  },
  {
    id: 'dusty-miller',
    name: 'DUSTY MILLER',
    description: '시간이 남긴 소재의 흔적을 디자인합니다.',
    image: '/images/landing/brand-dusty-miller.webp',
    imageAlt: 'DUSTY MILLER 로고',
  },
  {
    id: 'reweave',
    name: 'ReWeave',
    description: '데님 조각을 엮어 하나뿐인 제품을 만듭니다.',
    image: '/images/landing/brand-reweave.webp',
    imageAlt: 'ReWeave 로고',
  },
  {
    id: 'ocean-loop',
    name: 'OCEAN LOOP',
    description: '바다에서 수거한 자원을 일상으로 되돌립니다.',
    image: '/images/landing/brand-ocean-loop.webp',
    imageAlt: 'OCEAN LOOP 로고',
  },
]

export const products: Product[] = [
  { id: 'denim-circle-bag', name: '데님 서클 백', brand: 'REWEAVE', price: '129,000원', image: '/images/landing/product-denim-circle.webp', imagePosition: '50% 46%' },
  { id: 'patchwork-work-jacket', name: '패치워크 워크 자켓', brand: 'RE:CODE', price: '149,000원', image: '/images/landing/product-work-jacket.webp', imagePosition: '50% 50%' },
  { id: 'saddle-sling-bag', name: '새들 슬링 백', brand: 'DUSTY MILLER', price: '99,000원', image: '/images/landing/product-saddle-bag.webp', imagePosition: '50% 50%' },
  { id: 'patchwork-bucket-hat', name: '패치워크 버킷 햇', brand: 'OCEAN LOOP', price: '59,000원', image: '/images/landing/product-bucket-hat.webp', imagePosition: '50% 35%' },
  { id: 'market-tote', name: '마켓 토트', brand: 'RE:CODE', price: '79,000원', image: '/images/landing/product-market-tote.webp', imagePosition: '50% 50%' },
]

export const trustItems: TrustItem[] = [
  {
    id: 'verified-source',
    title: '검증된 출처',
    description: '모든 제품의 소재와 제작 과정을 확인합니다.',
  },
  {
    id: 'material-journey',
    title: '소재의 여정',
    description: '버려진 소재가 새 제품이 되는 이야기를 전합니다.',
  },
  {
    id: 'qr-tracking',
    title: 'QR 추적',
    description: 'QR 코드로 제품의 순환 기록을 살펴보세요.',
  },
]

export const footerNavigation: FooterNavigationGroup[] = [
  {
    title: '쇼핑',
    links: [
      { label: '전체 상품', href: '#curated-picks' },
      { label: '가방', href: '#curated-picks' },
      { label: '의류', href: '#curated-picks' },
      { label: '액세서리', href: '#curated-picks' },
      { label: '리빙', href: '#curated-picks' },
    ],
  },
  {
    title: '브랜드',
    links: [
      { label: '모든 브랜드', href: '#featured-brands' },
      { label: '추천 브랜드', href: '#featured-brands' },
      { label: '신규 브랜드', href: '#featured-brands' },
      { label: '브랜드 스토리', href: '#featured-brands' },
    ],
  },
  {
    title: '여정',
    links: [
      { label: '우리의 관점', href: '#trust' },
      { label: '소재의 여정', href: '#trust' },
      { label: '지속 가능성', href: '#trust' },
      { label: '업사이클링 가이드', href: '#trust' },
    ],
  },
  {
    title: '소개',
    links: [
      { label: '우리의 이야기', href: '#about' },
      { label: '영향력', href: '#trust' },
      { label: '블로그', href: '#about' },
      { label: '채용', href: '#about' },
    ],
  },
]
