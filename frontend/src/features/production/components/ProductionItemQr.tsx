import { useState } from 'react'
import { Button, Loading, Modal } from '../../../components/common/index.ts'
import { useProductionItemQr } from '../hooks/useProductionItemQr.ts'
import type { ProductionItem } from '../types/production.types.ts'
import { qrFileName } from '../utils/qrLabel.ts'
import './production.css'

export interface ProductionItemQrProps {
  batchId: number
  item: ProductionItem
}

/**
 * 개체 하나의 QR 이미지 생성·저장.
 *
 * **브랜드가 제품에 붙일 라벨을 얻는 지점이다.** 목록에는 주소만 있었다 — 주소를 복사해
 * 외부 QR 생성기에 넣으면 라벨마다 오류정정 수준과 여백이 달라지고, 그 차이는 인쇄해서
 * 스캔해 봐야 드러난다. 서버가 그린 이미지를 받아 그 갈라짐을 없앤다.
 *
 * **누르기 전에는 받지 않는다.** 배치 하나에 개체가 수십 개라 목록을 열자마자 전부 받으면
 * 보지도 않을 PNG 수십 장이 내려온다.
 *
 * **모달을 먼저 열고 안에서 기다린다.** 버튼을 눌러 두고 응답을 기다리게 하면 아무 일도
 * 일어나지 않는 몇 초가 생기고, 사용자는 버튼을 다시 누른다.
 */
export function ProductionItemQr({ batchId, item }: ProductionItemQrProps) {
  const [open, setOpen] = useState(false)
  const qr = useProductionItemQr(batchId, item.productItemId, open)

  return (
    <>
      <Button variant="secondary" size="sm" onClick={() => setOpen(true)}>
        QR 생성
      </Button>

      {/* 열렸을 때만 그린다 — 행마다 <dialog> 를 심어두면 개체 수만큼 쌓인다. */}
      {open && (
        <Modal
          open
          onClose={() => setOpen(false)}
          title={`QR 코드 · ${item.itemCode}`}
          footer={
            <>
              <Button variant="secondary" onClick={() => setOpen(false)}>
                닫기
              </Button>
              {/*
                저장은 링크로 한다. 스크립트로 내려받게 하면 팝업 차단·권한에 걸려 조용히
                실패하는 경로가 하나 더 생긴다. 이미지가 아직 없으면 버튼 자체를 두지 않는다 —
                눌러도 아무 일이 없는 버튼보다 없는 편이 낫다.
              */}
              {qr.url !== null && (
                <a
                  className="ui-button ui-button--primary ui-button--md"
                  href={qr.url}
                  download={qrFileName(item)}
                >
                  이미지 저장
                </a>
              )}
            </>
          }
        >
          <div className="item-qr">
            {qr.isPending && <Loading label="QR 이미지를 만드는 중" />}

            {qr.isError && (
              <div className="item-qr__error" role="alert">
                <p className="item-qr__message">{qr.errorMessage}</p>
                <Button variant="secondary" size="sm" onClick={qr.retry}>
                  다시 시도
                </Button>
              </div>
            )}

            {qr.url !== null && (
              <>
                <img
                  className="item-qr__image"
                  src={qr.url}
                  alt={`${item.itemCode} 개체의 QR 코드`}
                  width={320}
                  height={320}
                />
                {/*
                  무엇이 인쇄되는지 적는다. 스캔하면 열리는 화면이 공개 경로라는 사실을
                  라벨을 만드는 사람이 알아야 한다 — 목록 위의 안내는 여기까지 따라오지 않는다.
                */}
                <p className="item-qr__note">
                  저장해 제품에 인쇄하세요. 스캔하면 이 개체의 이력 화면이 열립니다 — 누구나
                  열 수 있는 공개 주소입니다.
                </p>
              </>
            )}
          </div>
        </Modal>
      )}
    </>
  )
}
