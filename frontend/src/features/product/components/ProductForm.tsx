import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import {
  Button,
  ErrorMessage,
  FileUploader,
  Input,
  Select,
} from '../../../components/common/index.ts'
import { formatWon } from '../../../utils/quantity.ts'
import { useCreateProductModel } from '../hooks/useCreateProductModel.ts'
import { useUpdateProductModel } from '../hooks/useUpdateProductModel.ts'
import { useUploadProductImage } from '../hooks/useUploadProductImage.ts'
import {
  PRODUCT_CATEGORIES,
  PRODUCT_CATEGORY_LABEL,
  PRODUCT_IMAGE_CONTENT_TYPES,
  PRODUCT_IMAGE_MAX_BYTES,
  type ProductCategory,
  type ProductModel,
} from '../types/product.types.ts'
import './product.css'

export interface ProductFormProps {
  /**
   * 고칠 제품. 없으면 등록 폼이다.
   *
   * 등록과 수정이 한 컴포넌트인 이유 — 필드 구성과 검증 규칙이 같다. 화면을 나누면
   * 판매가 상한이나 제품명 길이 같은 규칙이 한쪽에만 붙는 일이 생긴다.
   */
  product?: ProductModel
  onCreated?: (product: ProductModel) => void
  onUpdated?: (product: ProductModel) => void
}

/**
 * 판매가 상한.
 *
 * 서버는 0 이상만 검사하지만, 이 값을 넘으면 **카카오페이가 결제를 거부한다.** 그 사실이
 * 결제창에서 502 로만 드러나 사용자는 이유를 알 수 없다 — 입력 단계에서 막는다.
 */
const MAX_PRICE = 2_147_483_647

/** 폼 값. 대표 이미지는 파일이 아니라 **업로드로 받은 저장 키**를 들고 있다. */
interface ProductFormValues {
  name: string
  category: ProductCategory | ''
  /** 원 단위 정수 */
  price: number
  description: string
}

/**
 * 제품 등록 폼 (Brand).
 *
 * **등록은 두 번의 요청이다.**
 *
 *   ① POST /v1/product-images   파일 → storageKey
 *   ② POST /v1/product-models   { …, thumbnailUrl: storageKey }
 *
 * `thumbnailUrl` 이 서버에서 필수라 **이미지 없이는 제품을 만들 수 없다.** 그래서 파일을
 * 고르는 즉시 ①을 보내고 키를 폼이 들고 있다가 ②에 넣는다.
 *
 * 제출 시점에 둘을 묶지 않은 이유 — 그러면 "이미지는 올라갔는데 등록이 실패" 한 상태에서
 * 사용자가 다시 제출할 때 같은 파일을 또 올리고, 어디서 멈췄는지도 알 수 없다.
 *
 * ⚠️ 등록하지 않고 떠나면 올라간 파일은 아무도 가리키지 않는 채로 남는다. 서버에 삭제 API
 * 가 없어 화면이 되돌릴 수 없고, 정리는 서버 배치의 몫이다.
 */
export function ProductForm({ product, onCreated, onUpdated }: ProductFormProps) {
  const isEdit = product !== undefined

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, dirtyFields },
  } = useForm<ProductFormValues>({
    defaultValues: product
      ? {
          name: product.name,
          category: product.category,
          price: product.price,
          description: product.description,
        }
      : { name: '', category: '', description: '' },
  })

  const upload = useUploadProductImage()
  const create = useCreateProductModel()
  const update = useUpdateProductModel()

  // 방금 고른 파일의 미리보기. 서버를 거치지 않는다 — 조회 경로가 인증을 요구해서
  // `<img src>` 로는 안 보이고, 사용자가 고른 파일은 브라우저가 이미 갖고 있다.
  const [preview, setPreview] = useState<{ url: string; name: string } | null>(null)

  // blob URL 은 명시적으로 해제해야 메모리에서 사라진다. 외부 시스템(브라우저 URL 저장소)
  // 과의 동기화라 useEffect 가 맞는 자리다.
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview.url) }, [preview])

  const storageKey = upload.data?.storageKey ?? null

  function selectFile(files: File[]) {
    const file = files[0]
    if (!file) return

    // 형식은 서버도 거절하지만(PRODUCT_IMAGE_TYPE_NOT_ALLOWED) 업로드를 보내기 전에 막는다.
    // FileUploader 의 accept 는 파일 선택창의 힌트일 뿐 강제가 아니다.
    if (!PRODUCT_IMAGE_CONTENT_TYPES.includes(file.type)) {
      upload.reset()
      setPreview(null)
      return
    }

    setPreview({ url: URL.createObjectURL(file), name: file.name })
    upload.mutate(file)
  }

  /**
   * 무엇이 바뀌었는가 (수정 모드).
   *
   * **안 고친 필드는 아예 보내지 않는다.** 서버가 부분 수정이라, 원래 값을 다시 보내면
   * 결과는 같지만 "무엇을 고쳤는지" 가 요청에서 사라진다. 아무것도 안 바뀌었으면 서버가
   * 400 을 주므로 버튼도 잠근다.
   */
  const hasChanges =
    storageKey !== null ||
    Boolean(dirtyFields.name) ||
    Boolean(dirtyFields.price) ||
    Boolean(dirtyFields.description)

  const onSubmit = handleSubmit((values) => {
    if (isEdit) {
      if (!hasChanges) return
      update.mutate(
        {
          productModelId: product.productModelId,
          request: {
            ...(dirtyFields.name ? { name: values.name } : {}),
            ...(dirtyFields.price ? { price: values.price } : {}),
            ...(dirtyFields.description ? { description: values.description } : {}),
            ...(storageKey !== null ? { thumbnailUrl: storageKey } : {}),
          },
        },
        {
          onSuccess: (updated) => {
            setPreview(null)
            upload.reset()
            onUpdated?.(updated)
          },
        },
      )
      return
    }

    // 이미지가 없으면 서버가 400 을 준다. 버튼을 잠가 두었지만 방어로 한 번 더 본다.
    if (storageKey === null || values.category === '') return

    create.mutate(
      {
        name: values.name.trim(),
        category: values.category,
        price: values.price,
        description: values.description.trim(),
        thumbnailUrl: storageKey,
      },
      {
        onSuccess: (product) => {
          reset()
          setPreview(null)
          upload.reset()
          onCreated?.(product)
        },
      },
    )
  })

  return (
    <form onSubmit={onSubmit} noValidate className="product-form">
      <Input
        label="제품명"
        placeholder="예: 업사이클 데님 토트백"
        error={errors.name?.message}
        {...register('name', {
          required: '제품명을 입력해 주세요.',
          maxLength: { value: 200, message: '제품명은 200자를 넘을 수 없습니다.' },
        })}
      />

      <Select
        label="카테고리"
        placeholder="카테고리를 선택해 주세요"
        options={PRODUCT_CATEGORIES.map((category) => ({
          value: category,
          label: PRODUCT_CATEGORY_LABEL[category],
        }))}
        error={errors.category?.message}
        // 카테고리는 바꿀 수 없다. 이미 그 분류로 팔린 제품의 이력이 흔들린다 —
        // 서버도 수정 요청에서 이 필드를 받지 않는다.
        disabled={isEdit}
        {...register('category', { required: '카테고리를 선택해 주세요.' })}
      />

      {/* 비활성 입력만 두면 "왜 안 눌리지" 가 된다. */}
      {isEdit && (
        <p className="product-form__hint">카테고리는 등록 후 바꿀 수 없습니다.</p>
      )}

      <Input
        label="판매가 (원)"
        type="number"
        error={errors.price?.message}
        {...register('price', {
          required: '판매가를 입력해 주세요.',
          valueAsNumber: true,
          min: { value: 0, message: '판매가는 0원 이상이어야 합니다.' },
          // 서버는 막지 않지만 이 값을 넘으면 카카오페이가 결제를 거부한다.
          max: {
            value: MAX_PRICE,
            message: '판매가는 2,147,483,647원을 넘을 수 없습니다. 결제가 거부됩니다.',
          },
          // 서버가 소수를 조용히 버리지 않고 정수로 강제한다. 화면에서 먼저 막아
          // "129,000.5원을 넣었는데 129,000원으로 등록됨" 을 없앤다.
          validate: (value) =>
            Number.isInteger(value) ? true : '판매가는 원 단위 정수로 입력해 주세요.',
        })}
      />

      <div className="ui-field">
        <label className="ui-field__label" htmlFor="product-description">
          제품 설명
        </label>
        <textarea
          id="product-description"
          className="product-form__description"
          rows={5}
          placeholder="어떤 소재로 어떻게 만들었는지 적어 주세요."
          aria-invalid={errors.description ? true : undefined}
          {...register('description', { required: '제품 설명을 입력해 주세요.' })}
        />
        {errors.description && (
          <p className="ui-field__error" role="alert">
            {errors.description.message}
          </p>
        )}
      </div>

      <ThumbnailField
        preview={preview}
        isUploading={upload.isPending}
        uploadError={upload.isError ? upload.error : null}
        uploaded={storageKey !== null}
        onSelect={selectFile}
        // 수정할 때는 이미지를 고르지 않아도 된다. 안 고르면 쓰던 이미지가 그대로 남는다.
        optional={isEdit}
      />

      {create.isError && <ErrorMessage error={create.error} />}
      {update.isError && <ErrorMessage error={update.error} />}

      {isEdit ? (
        // 아무것도 바뀌지 않았으면 서버가 400 을 준다. 눌러 보고 알게 하지 않는다.
        <Button type="submit" loading={update.isPending} disabled={!hasChanges} fullWidth>
          수정 저장
        </Button>
      ) : (
        /* 이미지가 올라가기 전에는 제출할 수 없다 — 서버가 thumbnailUrl 을 필수로 받는다. */
        <Button type="submit" loading={create.isPending} disabled={storageKey === null} fullWidth>
          제품 등록
        </Button>
      )}

      {isEdit ? (
        <p className="product-form__note">
          판매 중에도 가격을 바꿀 수 있습니다. <strong>이미 결제된 주문은 영향받지
          않습니다</strong> — 주문 시점 단가가 그대로 남습니다.
        </p>
      ) : (
        /* 등록해도 바로 팔리지 않는다. 이 말을 빼면 사용자는 마켓에서 자기 제품을 찾다가
           없다고 생각한다. */
        <p className="product-form__note">
          등록하면 <strong>판매 준비(DRAFT)</strong> 상태로 저장됩니다. 생산으로 개체가
          만들어진 뒤 판매를 시작할 수 있습니다.
        </p>
      )}
    </form>
  )
}

/**
 * 대표 이미지.
 *
 * 파일을 고르는 즉시 업로드하므로 상태가 셋이다 — 올리는 중 / 올라감 / 실패.
 * 사용자가 "이 사진으로 등록된다" 를 확신할 수 있어야 하므로 세 경우를 모두 말한다.
 */
function ThumbnailField({
  preview,
  isUploading,
  uploadError,
  uploaded,
  onSelect,
  optional = false,
}: {
  preview: { url: string; name: string } | null
  isUploading: boolean
  uploadError: unknown
  uploaded: boolean
  onSelect: (files: File[]) => void
  /** 수정 모드. 고르지 않으면 쓰던 이미지가 그대로 남는다 */
  optional?: boolean
}) {
  return (
    <div className="product-form__thumbnail">
      <FileUploader
        label="대표 이미지"
        accept={PRODUCT_IMAGE_CONTENT_TYPES.join(',')}
        maxSizeBytes={PRODUCT_IMAGE_MAX_BYTES}
        disabled={isUploading}
        onChange={onSelect}
      />

      {preview && (
        <figure className="product-form__preview">
          {/* 서버를 거치지 않은 로컬 미리보기다. 등록 후 목록에 보이는 이미지는 인증이
              필요한 별도 경로에서 온다. */}
          <img src={preview.url} alt={`${preview.name} 미리보기`} />
          <figcaption>
            {isUploading && '이미지를 올리는 중…'}
            {!isUploading && uploaded && '이미지가 등록될 준비가 되었습니다.'}
          </figcaption>
        </figure>
      )}

      {uploadError !== null && <ErrorMessage error={uploadError} />}

      <p className="product-form__hint">
        JPG · PNG · WebP, {PRODUCT_IMAGE_MAX_BYTES / 1024 / 1024}MB 이하.{' '}
        {optional ? '고르지 않으면 지금 이미지가 그대로 남습니다.' : '대표 이미지는 필수입니다.'}
      </p>
    </div>
  )
}

/** 등록 결과 요약. 폼 밖에서 쓰라고 내보낸다 (페이지가 등록 후 화면을 정한다). */
export function ProductCreatedSummary({ product }: { product: ProductModel }) {
  return (
    <div className="product-form__created" role="status">
      <p className="product-form__created-title">{product.name} 등록이 완료되었습니다.</p>
      <dl>
        <div>
          <dt>카테고리</dt>
          <dd>{PRODUCT_CATEGORY_LABEL[product.category]}</dd>
        </div>
        <div>
          <dt>판매가</dt>
          <dd>{formatWon(product.price)}</dd>
        </div>
        <div>
          <dt>판매 가능 수량</dt>
          {/* 등록 직후에는 언제나 0 이다. 제품 모델은 설계도이고 팔 물건은 생산에서 나온다. */}
          <dd>{product.stockQuantity}개</dd>
        </div>
      </dl>
    </div>
  )
}
