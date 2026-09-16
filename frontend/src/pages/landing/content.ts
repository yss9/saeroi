export interface HomepageBrand {
  id: string
  name: string
  tagline: string
  description: string
  image: string
  imageAlt: string
}

export interface HomepageProduct {
  id: string
  name: string
  brand: string
  price: number
  material: string
  image: string
  imagePosition: string
}

export interface SaeroiValue {
  id: 'trace' | 'transform' | 'verify'
  title: string
  koreanTitle: string
  description: string
  image: string
  imageAlt: string
}

export interface FooterLink {
  label: string
  href: string
}

export interface FooterNavigationGroup {
  title: string
  links: FooterLink[]
}

export const homepageBrands: HomepageBrand[] = [
  {
    id: 'recode',
    name: 'RE:CODE',
    tagline: 'Archive, reimagined.',
    description: '재고 의류와 오래된 소재를 해체해 새로운 옷의 언어로 다시 설계합니다.',
    image: '/images/landing/brand-recode.webp',
    imageAlt: '재구성된 패브릭을 활용한 RE:CODE 브랜드 비주얼',
  },
  {
    id: 'dusty-miller',
    name: 'DUSTY MILLER',
    tagline: 'Marks of time.',
    description: '시간이 남긴 가죽과 원단의 흔적을 숨기지 않고 하나뿐인 표정으로 만듭니다.',
    image: '/images/landing/brand-dusty-miller.webp',
    imageAlt: '빈티지 소재를 활용한 DUSTY MILLER 브랜드 비주얼',
  },
  {
    id: 'reweave',
    name: 'ReWeave',
    tagline: 'Denim, connected.',
    description: '작게 남은 데님 조각을 다시 엮어 낭비 없는 새로운 형태를 제안합니다.',
    image: '/images/landing/brand-reweave.webp',
    imageAlt: '데님 조각을 재구성한 ReWeave 브랜드 비주얼',
  },
  {
    id: 'ocean-loop',
    name: 'OCEAN LOOP',
    tagline: 'From tide to form.',
    description: '바다와 해안에서 수거한 자원을 오래 사용할 수 있는 일상 제품으로 되돌립니다.',
    image: '/images/landing/brand-ocean-loop.webp',
    imageAlt: '해양 회수 소재를 활용한 OCEAN LOOP 브랜드 비주얼',
  },
]

export const saeroiValues: SaeroiValue[] = [
  {
    id: 'trace',
    title: 'TRACE',
    koreanTitle: '시작을 확인하다',
    description: '어떤 소재에서 시작되었는지, 제품의 출처를 확인합니다.',
    image: '/images/landing/journey/trace-material.webp',
    imageAlt: '재사용을 기다리는 폐섬유 소재',
  },
  {
    id: 'transform',
    title: 'TRANSFORM',
    koreanTitle: '변화를 따라가다',
    description: '버려진 소재가 새로운 제품으로 다시 태어나는 과정을 따라갑니다.',
    image: '/images/landing/journey/transform-process.webp',
    imageAlt: '폐섬유를 새로운 제품으로 제작하는 봉제 과정',
  },
  {
    id: 'verify',
    title: 'VERIFY',
    koreanTitle: '기록으로 확인하다',
    description: 'QR을 통해 소재와 제작 과정의 기록을 직접 확인합니다.',
    image: '/images/landing/journey/verify-qr.webp',
    imageAlt: '제품 QR을 스캔해 소재와 제작 이력을 확인하는 모습',
  },
]

export const footerNavigation: FooterNavigationGroup[] = [
  {
    title: 'ABOUT',
    links: [{ label: '우리의 이야기', href: '/about' }],
  },
  {
    title: 'CUSTOMER',
    links: [
      { label: '문의하기', href: '#support' },
      { label: '자주 묻는 질문', href: '#faq' },
    ],
  },
]
