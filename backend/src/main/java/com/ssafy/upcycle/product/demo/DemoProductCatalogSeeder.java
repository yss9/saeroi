package com.ssafy.upcycle.product.demo;

import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.entity.ProductSubcategory;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.production.entity.ProductionBatch;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.geolocation.GeoCoordinates;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈·SHOP 데모 상품을 실제 주문 가능한 도메인 데이터로 준비한다.
 *
 * <p>{@code app.demo-catalog.enabled=true} 일 때만 빈으로 생성된다. 데모 브랜드 이메일,
 * 상품명, 배치 코드가 고정되어 있어 재기동해도 중복되지 않는다. 이미 판매된 개체는 다시
 * 만들지 않으며, 설정 수량보다 총 생산 개체가 적은 부분 초기화 상태만 보완한다.
 *
 * <p><b>상품 48개는 브랜드 4개 × 최종 분류 12개다.</b> 어느 2단 메뉴를 눌러도 브랜드마다
 * 하나씩 네 개가 나오므로, SHOP 의 분류 필터가 실제로 걸리는지 화면에서 바로 보인다.
 *
 * <p><b>이미지는 여기서 다루지 않는다.</b> 상품은 한 번 만들어지면 그대로지만 사진은 임시
 * 이미지로 시작해 나중에 진짜 사진으로 바뀐다 — 그 교체가 상품을 지우고 다시 만드는 일이
 * 되면 id·SKU·재고·주문 연결이 끊긴다. {@link DemoProductImageBinder} 가 이미지만 갈아
 * 끼우는 경로를 따로 갖는다.
 */
@Component
@ConditionalOnProperty(name = "app.demo-catalog.enabled", havingValue = "true")
public class DemoProductCatalogSeeder implements ApplicationRunner {

    public static final String DEMO_EMAIL_PREFIX = "demo-catalog-";
    public static final String DEMO_EMAIL_DOMAIN = "@saeroi.local";

    private static final Logger log = LoggerFactory.getLogger(DemoProductCatalogSeeder.class);
    private static final long USED_MATERIAL_GRAMS_PER_ITEM = 500L;

    private static final List<DemoBrand> BRANDS = List.of(
            new DemoBrand(
                    "reweave",
                    "ReWeave",
                    "101-81-00001",
                    "0x000000000000000000000000000000000000d001",
                    "수거한 데님을 실 단위로 되돌려 다시 짭니다. 인디고 한 가지 색조 안에서 "
                            + "패널 분할과 스티치로만 면을 나누며, 장식을 더하는 대신 구조를 "
                            + "드러냅니다. 옷과 가방, 리빙 소품까지 같은 원단으로 이어집니다."),
            new DemoBrand(
                    "recode",
                    "RE:CODE",
                    "101-81-00002",
                    "0x000000000000000000000000000000000000d002",
                    "재고로 남은 작업복을 해체해 다시 조합합니다. 원단의 마모와 봉제 흔적을 "
                            + "지우지 않아 조각 배치가 제품마다 다릅니다. 포켓과 웨빙이 반복되는 "
                            + "유틸리티 무드로 옷과 가방을 함께 만듭니다."),
            new DemoBrand(
                    "dusty-miller",
                    "DUSTY MILLER",
                    "101-81-00003",
                    "0x000000000000000000000000000000000000d003",
                    "가구와 의류 공정에서 남은 자투리 가죽과 스웨이드를 모읍니다. 조각의 크기에 "
                            + "맞춰 재단하므로 이음선이 그대로 디자인이 되고, 봉제를 줄여 소재의 "
                            + "드레이프를 살립니다. 가장 작은 조각은 소품이 됩니다."),
            new DemoBrand(
                    "ocean-loop",
                    "OCEAN LOOP",
                    "101-81-00004",
                    "0x000000000000000000000000000000000000d004",
                    "바다에서 걷어 올린 폐어망과 수거 플라스틱에서 실을 뽑습니다. 가볍고 물에 "
                            + "강한 성질을 그대로 살려 젖어도 무거워지지 않는 옷과 가방을 "
                            + "만듭니다. 쓰임이 끝나면 다시 실로 돌아갑니다."));

    /**
     * 데모 상품 48개. <b>브랜드 4개 × 최종 분류 12개</b>로, 어느 2단 메뉴를 눌러도 브랜드마다
     * 하나씩 네 개가 나온다.
     *
     * <p><b>상품명이 곧 식별자다</b> ({@link #findDemoModel} 이 브랜드와 이름으로 찾는다).
     * 한 번 시드한 뒤 이름을 바꾸면 옛 상품이 남은 채 새 상품이 하나 더 생기고, 그 옛 상품에
     * 이미 걸린 주문은 목록에 없는 상품을 가리키게 된다. <b>가격·설명·이미지는 언제든 바꿔도
     * 되지만 이름·키·배치 코드는 그렇지 않다.</b>
     *
     * <p>데모 키는 개체 코드({@code DEMO-{KEY}-001})와 QR 토큰의 씨앗이고, 진짜 사진을 넣을
     * 폴더 이름이기도 하다 ({@link DemoProductImageDirectory}).
     */
    private static final List<DemoProduct> PRODUCTS = List.of(
            // ReWeave — 수거 데님을 실 단위로 되돌려 다시 짠 재직조 데님. 인디고 단색조의 구조적 실루엣.
            new DemoProduct(
                    "reweave-top",
                    "reweave",
                    "리위브 데님 셔츠",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.TOP,
                    119_000,
                    "해체한 데님을 다시 짜 만든 원단으로 각을 살린 셔츠입니다. 세탁할수록 결이 부드러워집니다.",
                    "DEMO-PB-RW-TOP"),
            new DemoProduct(
                    "reweave-outer",
                    "reweave",
                    "인디고 패널 코트",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.OUTER,
                    189_000,
                    "서로 다른 시기의 데님을 패널로 나눠 이은 코트입니다. 패널마다 인디고 농도가 다릅니다.",
                    "DEMO-PB-RW-OUT"),
            new DemoProduct(
                    "reweave-pants",
                    "reweave",
                    "스트레이트 리위브 팬츠",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.PANTS,
                    139_000,
                    "밑단까지 곧게 떨어지는 실루엣입니다. 앞뒤 패널의 결 방향을 다르게 두었습니다.",
                    "DEMO-PB-RW-PNT"),
            new DemoProduct(
                    "reweave-skirt",
                    "reweave",
                    "인디고 랩 스커트",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.SKIRT,
                    109_000,
                    "여미는 위치로 길이를 조절하는 랩 구조입니다. 한 장의 재직조 원단을 그대로 썼습니다.",
                    "DEMO-PB-RW-SKT"),
            new DemoProduct(
                    "reweave-dress",
                    "reweave",
                    "데님 슬립 드레스",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.DRESS,
                    149_000,
                    "얇게 짠 데님으로 떨어뜨린 슬립 실루엣입니다. 겉옷과 겹쳐 입기 좋습니다.",
                    "DEMO-PB-RW-DRS"),
            new DemoProduct(
                    "reweave-tote",
                    "reweave",
                    "리위브 스퀘어 토트",
                    ProductCategory.BAG,
                    ProductSubcategory.TOTE,
                    99_000,
                    "각진 바닥으로 세워지는 토트백입니다. 노트북이 눕는 폭입니다.",
                    "DEMO-PB-RW-TOT"),
            new DemoProduct(
                    "denim-circle",
                    "reweave",
                    "데님 서클 백",
                    ProductCategory.BAG,
                    ProductSubcategory.SHOULDER,
                    129_000,
                    "폐데님 조각의 곡선을 살려 완성한 원형 숄더백입니다. 소재마다 다른 색과 흔적이 하나뿐인 표정을 만듭니다.",
                    "DEMO-PB-DENIM"),
            new DemoProduct(
                    "reweave-crossbody",
                    "reweave",
                    "인디고 미니 크로스백",
                    ProductCategory.BAG,
                    ProductSubcategory.CROSSBODY,
                    89_000,
                    "지갑과 휴대폰만 들어가는 최소 부피입니다. 스트랩 길이를 넓게 조절합니다.",
                    "DEMO-PB-RW-CRS"),
            new DemoProduct(
                    "reweave-backpack",
                    "reweave",
                    "리위브 롤탑 백팩",
                    ProductCategory.BAG,
                    ProductSubcategory.BACKPACK,
                    159_000,
                    "말아 닫는 상단으로 부피가 변합니다. 폐면 웨빙으로 어깨끈을 짰습니다.",
                    "DEMO-PB-RW-BKP"),
            new DemoProduct(
                    "reweave-wallet",
                    "reweave",
                    "데님 하프 월렛",
                    ProductCategory.WALLET,
                    null,
                    69_000,
                    "카드 여섯 장과 지폐가 접히는 반지갑입니다. 모서리를 겹쳐 박아 보강했습니다.",
                    "DEMO-PB-RW-WLT"),
            new DemoProduct(
                    "reweave-accessory",
                    "reweave",
                    "인디고 워크 캡",
                    ProductCategory.ACCESSORY,
                    null,
                    59_000,
                    "챙을 짧게 둔 작업모 형태입니다. 여섯 조각 패널의 색이 미세하게 다릅니다.",
                    "DEMO-PB-RW-ACC"),
            new DemoProduct(
                    "reweave-home",
                    "reweave",
                    "리위브 데님 쿠션 커버",
                    ProductCategory.HOME,
                    null,
                    79_000,
                    "45cm 정사각 커버입니다. 뒷면은 봉투식이라 속통을 쉽게 뺍니다.",
                    "DEMO-PB-RW-HOM"),
            // RE:CODE — 재고·폐워크웨어를 해체해 재조합한 패치워크. 포켓과 웨빙이 반복되는 유틸리티 무드.
            new DemoProduct(
                    "recode-top",
                    "recode",
                    "패치워크 오버 셔츠",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.TOP,
                    129_000,
                    "작업복 세 벌 분량의 원단을 조합한 오버핏 셔츠입니다. 조각 배치가 개체마다 다릅니다.",
                    "DEMO-PB-RC-TOP"),
            new DemoProduct(
                    "work-jacket",
                    "recode",
                    "패치워크 워크 자켓",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.OUTER,
                    149_000,
                    "남겨진 워크웨어 원단을 해체하고 다시 조합한 패치워크 자켓입니다. 원단의 마모와 봉제 흔적을 그대로 살렸습니다.",
                    "DEMO-PB-JACKET"),
            new DemoProduct(
                    "recode-pants",
                    "recode",
                    "리코드 카고 팬츠",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.PANTS,
                    139_000,
                    "무릎 위 사선 포켓이 걸을 때 열립니다. 밑단은 끈으로 조입니다.",
                    "DEMO-PB-RC-PNT"),
            new DemoProduct(
                    "recode-skirt",
                    "recode",
                    "유틸리티 카고 스커트",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.SKIRT,
                    119_000,
                    "팬츠의 포켓 구조를 스커트에 옮겼습니다. 앞면 분할로 보폭을 확보했습니다.",
                    "DEMO-PB-RC-SKT"),
            new DemoProduct(
                    "recode-dress",
                    "recode",
                    "패치워크 셔츠 드레스",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.DRESS,
                    169_000,
                    "셔츠를 길게 늘인 원피스입니다. 허리 끈을 풀면 코트처럼 걸칩니다.",
                    "DEMO-PB-RC-DRS"),
            new DemoProduct(
                    "market-tote",
                    "recode",
                    "마켓 토트",
                    ProductCategory.BAG,
                    ProductSubcategory.TOTE,
                    79_000,
                    "남은 원단을 넉넉한 크기로 다시 구성한 데일리 토트백입니다. 장보기와 일상 수납에 모두 사용할 수 있습니다.",
                    "DEMO-PB-TOTE"),
            new DemoProduct(
                    "recode-shoulder",
                    "recode",
                    "리코드 유틸리티 숄더백",
                    ProductCategory.BAG,
                    ProductSubcategory.SHOULDER,
                    109_000,
                    "겉면 포켓 세 개를 웨빙으로 고정했습니다. 필요에 따라 위치를 옮깁니다.",
                    "DEMO-PB-RC-SHD"),
            new DemoProduct(
                    "recode-crossbody",
                    "recode",
                    "웨빙 크로스 새첼",
                    ProductCategory.BAG,
                    ProductSubcategory.CROSSBODY,
                    89_000,
                    "회수한 웨빙을 그대로 스트랩으로 썼습니다. 뚜껑은 버클 하나로 여닫습니다.",
                    "DEMO-PB-RC-CRS"),
            new DemoProduct(
                    "recode-backpack",
                    "recode",
                    "리코드 툴 백팩",
                    ProductCategory.BAG,
                    ProductSubcategory.BACKPACK,
                    169_000,
                    "공구 가방의 칸 구조를 그대로 옮겼습니다. 안쪽이 통으로 열립니다.",
                    "DEMO-PB-RC-BKP"),
            new DemoProduct(
                    "recode-wallet",
                    "recode",
                    "캔버스 지퍼 월렛",
                    ProductCategory.WALLET,
                    null,
                    59_000,
                    "지퍼 한 줄로 전체가 열리는 장지갑입니다. 캔버스가 두꺼워 형태가 유지됩니다.",
                    "DEMO-PB-RC-WLT"),
            new DemoProduct(
                    "recode-accessory",
                    "recode",
                    "캔버스 웹 벨트",
                    ProductCategory.ACCESSORY,
                    null,
                    49_000,
                    "회수 웨빙에 금속 버클만 더했습니다. 길이를 잘라 맞춥니다.",
                    "DEMO-PB-RC-ACC"),
            new DemoProduct(
                    "recode-home",
                    "recode",
                    "워크웨어 스토리지 바스켓",
                    ProductCategory.HOME,
                    null,
                    69_000,
                    "접으면 납작해지는 사각 수납함입니다. 원단이 두꺼워 비워도 섭니다.",
                    "DEMO-PB-RC-HOM"),
            // DUSTY MILLER — 가구·의류 공정에서 남은 자투리 가죽과 스웨이드. 곡선과 드레이프 중심.
            new DemoProduct(
                    "dusty-top",
                    "dusty-miller",
                    "스웨이드 패널 블라우스",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.TOP,
                    179_000,
                    "작은 스웨이드 조각을 세로로 이어 흐름을 만들었습니다. 조각마다 결의 방향이 다릅니다.",
                    "DEMO-PB-DM-TOP"),
            new DemoProduct(
                    "dusty-outer",
                    "dusty-miller",
                    "레더 패치 블루종",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.OUTER,
                    359_000,
                    "가죽 조각을 이어 만든 블루종입니다. 이음선이 그대로 디자인이 됩니다.",
                    "DEMO-PB-DM-OUT"),
            new DemoProduct(
                    "dusty-pants",
                    "dusty-miller",
                    "스웨이드 테이퍼드 팬츠",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.PANTS,
                    249_000,
                    "무릎 아래로 좁아지는 실루엣입니다. 스웨이드가 얇아 무겁지 않습니다.",
                    "DEMO-PB-DM-PNT"),
            new DemoProduct(
                    "dusty-skirt",
                    "dusty-miller",
                    "레더 A라인 스커트",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.SKIRT,
                    229_000,
                    "허리에서 아래로 넓어지는 한 장 구조입니다. 앞면 이음선을 중심에 두었습니다.",
                    "DEMO-PB-DM-SKT"),
            new DemoProduct(
                    "dusty-dress",
                    "dusty-miller",
                    "스웨이드 슬립 드레스",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.DRESS,
                    279_000,
                    "몸을 따라 떨어지는 드레이프입니다. 안감 없이 한 겹으로 만들었습니다.",
                    "DEMO-PB-DM-DRS"),
            new DemoProduct(
                    "dusty-tote",
                    "dusty-miller",
                    "소프트 레더 토트",
                    ProductCategory.BAG,
                    ProductSubcategory.TOTE,
                    259_000,
                    "형태를 잡지 않아 내용물에 따라 모양이 바뀝니다. 손잡이만 보강했습니다.",
                    "DEMO-PB-DM-TOT"),
            new DemoProduct(
                    "dusty-shoulder",
                    "dusty-miller",
                    "더스티 문 숄더백",
                    ProductCategory.BAG,
                    ProductSubcategory.SHOULDER,
                    219_000,
                    "초승달 곡선으로 재단해 겨드랑이에 붙습니다. 조각 이음이 곡선을 따라갑니다.",
                    "DEMO-PB-DM-SHD"),
            new DemoProduct(
                    "saddle-bag",
                    "dusty-miller",
                    "새들 슬링 백",
                    ProductCategory.BAG,
                    ProductSubcategory.CROSSBODY,
                    99_000,
                    "사용 후 회수된 원단을 유연한 곡선 형태로 재단한 슬링 백입니다. 가볍게 멜 수 있는 일상용 수납 구조를 담았습니다.",
                    "DEMO-PB-SADDLE"),
            new DemoProduct(
                    "dusty-backpack",
                    "dusty-miller",
                    "레더 라운드 백팩",
                    ProductCategory.BAG,
                    ProductSubcategory.BACKPACK,
                    289_000,
                    "둥근 바디에 가죽 끈을 달았습니다. 상단을 끈으로 오므려 닫습니다.",
                    "DEMO-PB-DM-BKP"),
            new DemoProduct(
                    "dusty-wallet",
                    "dusty-miller",
                    "스웨이드 카드 월렛",
                    ProductCategory.WALLET,
                    null,
                    89_000,
                    "카드 네 장과 지폐 한 겹입니다. 주머니에서 두께가 느껴지지 않습니다.",
                    "DEMO-PB-DM-WLT"),
            new DemoProduct(
                    "dusty-accessory",
                    "dusty-miller",
                    "레더 헤어 바레트",
                    ProductCategory.ACCESSORY,
                    null,
                    69_000,
                    "가장 작은 조각까지 쓰는 품목입니다. 나무 핀과 조합했습니다.",
                    "DEMO-PB-DM-ACC"),
            new DemoProduct(
                    "dusty-home",
                    "dusty-miller",
                    "레더 트레이 세트",
                    ProductCategory.HOME,
                    null,
                    119_000,
                    "네 귀를 접어 세우는 트레이 두 점입니다. 열쇠와 반지를 놓는 크기입니다.",
                    "DEMO-PB-DM-HOM"),
            // OCEAN LOOP — 폐어망·해양 수거 플라스틱에서 뽑은 리사이클 원사. 가볍고 물에 강한 기능성.
            new DemoProduct(
                    "ocean-top",
                    "ocean-loop",
                    "리사이클 니트 티",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.TOP,
                    79_000,
                    "폐플라스틱에서 뽑은 실로 짠 반팔 니트입니다. 땀이 빨리 마릅니다.",
                    "DEMO-PB-OL-TOP"),
            new DemoProduct(
                    "ocean-outer",
                    "ocean-loop",
                    "오션 윈드브레이커",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.OUTER,
                    159_000,
                    "폐어망 원사로 짠 방풍 외피입니다. 접으면 자체 주머니에 들어갑니다.",
                    "DEMO-PB-OL-OUT"),
            new DemoProduct(
                    "ocean-pants",
                    "ocean-loop",
                    "리사이클 트랙 팬츠",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.PANTS,
                    99_000,
                    "옆선에 배수 구조를 둔 트랙 팬츠입니다. 젖어도 무거워지지 않습니다.",
                    "DEMO-PB-OL-PNT"),
            new DemoProduct(
                    "ocean-skirt",
                    "ocean-loop",
                    "오션 플리츠 스커트",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.SKIRT,
                    89_000,
                    "열로 고정한 주름이 세탁 후에도 남습니다. 걸을 때 주름이 갈라집니다.",
                    "DEMO-PB-OL-SKT"),
            new DemoProduct(
                    "ocean-dress",
                    "ocean-loop",
                    "리사이클 저지 드레스",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.DRESS,
                    109_000,
                    "한 장으로 떨어지는 저지 원피스입니다. 구김이 남지 않습니다.",
                    "DEMO-PB-OL-DRS"),
            new DemoProduct(
                    "ocean-tote",
                    "ocean-loop",
                    "오션 메쉬 토트",
                    ProductCategory.BAG,
                    ProductSubcategory.TOTE,
                    69_000,
                    "어망 구조를 그대로 살린 메쉬입니다. 물이 그대로 빠집니다.",
                    "DEMO-PB-OL-TOT"),
            new DemoProduct(
                    "ocean-shoulder",
                    "ocean-loop",
                    "웨이브 숄더백",
                    ProductCategory.BAG,
                    ProductSubcategory.SHOULDER,
                    79_000,
                    "물결 모양 퀼팅을 넣어 가벼운 부피감을 만들었습니다.",
                    "DEMO-PB-OL-SHD"),
            new DemoProduct(
                    "ocean-crossbody",
                    "ocean-loop",
                    "오션 슬링 포켓",
                    ProductCategory.BAG,
                    ProductSubcategory.CROSSBODY,
                    59_000,
                    "가슴에 붙여 메는 최소 크기입니다. 스트랩이 본체와 한 몸입니다.",
                    "DEMO-PB-OL-CRS"),
            new DemoProduct(
                    "ocean-backpack",
                    "ocean-loop",
                    "타이드 롤탑 백팩",
                    ProductCategory.BAG,
                    ProductSubcategory.BACKPACK,
                    129_000,
                    "말아 닫아 물이 들어가지 않습니다. 젖은 옷을 넣는 별도 칸이 있습니다.",
                    "DEMO-PB-OL-BKP"),
            new DemoProduct(
                    "ocean-wallet",
                    "ocean-loop",
                    "리사이클 지퍼 월렛",
                    ProductCategory.WALLET,
                    null,
                    39_000,
                    "방수 원단 반지갑입니다. 물가에서 꺼내 써도 젖지 않습니다.",
                    "DEMO-PB-OL-WLT"),
            new DemoProduct(
                    "bucket-hat",
                    "ocean-loop",
                    "패치워크 버킷 햇",
                    ProductCategory.ACCESSORY,
                    null,
                    59_000,
                    "수거된 소재의 서로 다른 결을 이어 만든 버킷 햇입니다. 조각 배치가 제품마다 달라 같은 모양이 반복되지 않습니다.",
                    "DEMO-PB-HAT"),
            new DemoProduct(
                    "ocean-home",
                    "ocean-loop",
                    "오션 라운드 러그",
                    ProductCategory.HOME,
                    null,
                    149_000,
                    "폐어망 원사를 굵게 꼬아 감아 만든 원형 러그입니다. 물걸레로 닦입니다.",
                    "DEMO-PB-OL-HOM"));

    private final UserRepository userRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final ProductModelRepository productModelRepository;
    private final ProductionBatchRepository productionBatchRepository;
    private final ProductItemRepository productItemRepository;
    private final DemoProductImageBinder imageBinder;
    private final DemoBrandLogoBinder brandLogoBinder;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final int stockPerProduct;

    public DemoProductCatalogSeeder(
            UserRepository userRepository,
            CompanyProfileRepository companyProfileRepository,
            ProductModelRepository productModelRepository,
            ProductionBatchRepository productionBatchRepository,
            ProductItemRepository productItemRepository,
            DemoProductImageBinder imageBinder,
            DemoBrandLogoBinder brandLogoBinder,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.demo-catalog.stock-per-product:10}") int stockPerProduct) {
        if (stockPerProduct <= 0) {
            throw new IllegalArgumentException("Demo product stock must be positive");
        }
        this.userRepository = userRepository;
        this.companyProfileRepository = companyProfileRepository;
        this.productModelRepository = productModelRepository;
        this.productionBatchRepository = productionBatchRepository;
        this.productItemRepository = productItemRepository;
        this.imageBinder = imageBinder;
        this.brandLogoBinder = brandLogoBinder;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.stockPerProduct = stockPerProduct;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, SeededBrand> brands = new HashMap<>();
        for (DemoBrand definition : BRANDS) {
            brands.put(definition.key(), seedBrand(definition));
        }
        for (DemoProduct definition : PRODUCTS) {
            seedProduct(definition, brands.get(definition.brandKey()));
        }
        log.info(
                "Demo product catalog ready: brands={} products={} initialStockPerProduct={}",
                BRANDS.size(),
                PRODUCTS.size(),
                stockPerProduct);
    }

    private SeededBrand seedBrand(DemoBrand definition) {
        String email = DEMO_EMAIL_PREFIX + definition.key() + DEMO_EMAIL_DOMAIN;
        User user = userRepository.findByEmail(email).orElseGet(() -> userRepository.saveAndFlush(
                User.register(
                        email,
                        passwordEncoder.encode(UUID.randomUUID().toString()),
                        definition.companyName() + " 담당자",
                        "02-0000-0000",
                        UserRole.BRAND)));
        if (user.getRole() != UserRole.BRAND || !user.isActive()) {
            throw new IllegalStateException("Demo catalog account must be an active BRAND: " + email);
        }

        CompanyProfile profile = companyProfileRepository.findById(user.getId()).orElseGet(() ->
                companyProfileRepository.save(CompanyProfile.register(
                        user.getId(),
                        definition.companyName(),
                        definition.businessNumber(),
                        "04782",
                        "서울특별시 성동구 연무장길 1",
                        "SAEROI DEMO",
                        new GeoCoordinates(
                                new BigDecimal("37.5446000"),
                                new BigDecimal("127.0559000")))));

        seedBrandProfile(profile, definition);
        return new SeededBrand(user, definition.walletAddress());
    }

    /**
     * 브랜드의 소개글과 로고. <b>비어 있을 때만 채운다.</b>
     *
     * <p>이미 쓰여 있는 소개글을 재기동이 덮어쓰면 브랜드가 고쳐 둔 글이 사라진다. 로고도
     * 같은 판단을 {@link DemoBrandLogoBinder} 가 한다.
     */
    private void seedBrandProfile(CompanyProfile profile, DemoBrand definition) {
        if (profile.getIntroduction() == null || profile.getIntroduction().isBlank()) {
            profile.updateIntroduction(definition.introduction());
        }
        brandLogoBinder.bind(profile, definition.key());
        companyProfileRepository.save(profile);
    }

    private void seedProduct(DemoProduct definition, SeededBrand brand) {
        Long brandId = brand.user().getId();
        ProductModel model = findDemoModel(brandId, definition.name());
        DemoProductImageUpload newProductImages = null;

        if (model == null) {
            // 등록 전에 대표 이미지 키가 필요하다 — thumbnail_url 이 NOT NULL 이다.
            // 갤러리는 상품 행이 생긴 뒤에 건다.
            newProductImages = imageBinder.upload(
                    brandId, definition.key(), definition.category(), definition.subcategory());
            model = productModelRepository.saveAndFlush(ProductModel.register(
                    definition.name(),
                    definition.category(),
                    definition.subcategory(),
                    brandId,
                    definition.price(),
                    definition.description(),
                    newProductImages.thumbnailKey()));
        } else {
            requireCategory(model, definition);
            if (model.getStatus() == ProductModelStatus.DISCONTINUED) {
                log.warn("Skipping discontinued demo product: {}", definition.name());
                return;
            }
            // 대표 이미지는 여기서 건드리지 않는다. 진짜 사진으로 바꿀 때인지는
            // imageBinder 가 판단한다 — 매 기동마다 다시 올리면 저장소에 쓰이지 않는
            // 파일이 쌓이고 thumbnail_url 이 계속 달라진다.
            model.update(
                    definition.name(), definition.price(), definition.description(), null);
            // 하위 분류가 생기기 전에 시드된 행은 값이 비어 있다. 재기동 때 채워야 SHOP
            // 2단 메뉴에 들어온다 — 브랜드가 손으로 고쳐 줄 데이터가 아니다.
            if (definition.subcategory() != null
                    && model.getSubcategory() != definition.subcategory()) {
                model.assignSubcategory(definition.subcategory());
            }
        }

        ProductionBatch batch = seedBatch(definition, brand, model);
        int addedStock = seedItems(definition, model, batch);
        if (model.getStatus() == ProductModelStatus.DRAFT) {
            model.putOnSale();
        } else if (model.getStatus() == ProductModelStatus.SOLD_OUT && addedStock > 0) {
            model.restock();
        }
        productModelRepository.saveAndFlush(model);

        if (newProductImages != null) {
            imageBinder.applyGallery(brandId, model.getId(), newProductImages);
        } else {
            imageBinder.refresh(model, definition.key(), brandId);
        }
    }

    private ProductModel findDemoModel(Long brandId, String name) {
        List<ProductModel> matches = productModelRepository.findAllByBrandId(brandId).stream()
                .filter(model -> model.getName().equals(name))
                .toList();
        if (matches.size() > 1) {
            throw new IllegalStateException("Duplicate demo product model: " + name);
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private ProductionBatch seedBatch(
            DemoProduct definition, SeededBrand brand, ProductModel model) {
        ProductionBatch batch = productionBatchRepository
                .findByBatchCode(definition.batchCode())
                .orElseGet(() -> productionBatchRepository.saveAndFlush(ProductionBatch.open(
                        definition.batchCode(),
                        definition.name() + " 데모 생산",
                        model.getId(),
                        stockPerProduct,
                        brand.user().getId(),
                        brand.walletAddress())));
        if (!batch.getProductModelId().equals(model.getId())
                || !batch.getProducerId().equals(brand.user().getId())) {
            throw new IllegalStateException(
                    "Demo batch belongs to a different product or brand: " + definition.batchCode());
        }
        if (batch.getProducedQuantity() != stockPerProduct) {
            throw new IllegalStateException(
                    "Demo stock setting differs from the existing batch: "
                            + definition.batchCode());
        }

        ProductionBatchStatus status = batch.getStatus();
        if (status == ProductionBatchStatus.FAILED) {
            batch.reopen();
            status = batch.getStatus();
        }
        if (status == ProductionBatchStatus.DRAFT) {
            batch.startSigning();
            status = batch.getStatus();
        }
        if (status == ProductionBatchStatus.SIGNING) {
            batch.confirm(
                    0L,
                    stockPerProduct * USED_MATERIAL_GRAMS_PER_ITEM,
                    LocalDateTime.now(clock));
        } else if (status != ProductionBatchStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Demo batch cannot seed stock while " + status + ": " + definition.batchCode());
        }
        return productionBatchRepository.save(batch);
    }

    private int seedItems(
            DemoProduct definition, ProductModel model, ProductionBatch batch) {
        List<ProductItem> existing = productItemRepository.findAllByProductionBatchId(batch.getId());
        Set<Integer> existingSerials = new HashSet<>();
        for (ProductItem item : existing) {
            existingSerials.add(item.getSerialNumber());
        }

        int added = 0;
        for (int serial = 1; serial <= stockPerProduct; serial++) {
            if (existingSerials.contains(serial)) {
                continue;
            }
            String seed = definition.key() + ":" + serial;
            String qrToken = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8))
                    .toString()
                    .replace("-", "");
            productItemRepository.save(ProductItem.produce(
                    batch.getId(),
                    model.getId(),
                    serial,
                    "DEMO-" + definition.key().toUpperCase() + "-" + "%03d".formatted(serial),
                    qrToken));
            added++;
        }
        return added;
    }

    private void requireCategory(ProductModel model, DemoProduct definition) {
        if (model.getCategory() != definition.category()) {
            throw new IllegalStateException(
                    "Demo product category mismatch: " + definition.name());
        }
    }

    private record DemoBrand(
            String key,
            String companyName,
            String businessNumber,
            String walletAddress,
            /** 소비자 BRANDS 화면에 나가는 소개글. 비어 있는 브랜드에만 채운다. */
            String introduction) {}

    private record SeededBrand(User user, String walletAddress) {}

    /**
     * 데모 상품 하나의 정의.
     *
     * <p>{@code name} · {@code key} · {@code batchCode} 는 <b>식별자라 바꿀 수 없다.</b>
     * 이름은 상품 행을, 키는 개체 코드와 QR 토큰을, 배치 코드는 생산 배치를 찾는 값이다.
     * 한 번 시드한 뒤 바꾸면 옛 행이 남은 채 새 행이 하나 더 생긴다.
     *
     * <p>{@code thumbnailUrl} 이 없는 것은 의도적이다 — 이미지는 정의가 아니라
     * {@link DemoProductImageBinder} 가 정한다.
     */
    private record DemoProduct(
            String key,
            String brandKey,
            String name,
            ProductCategory category,
            /** 2단 메뉴가 없는 분류(WALLET·ACCESSORY·HOME)는 {@code null} 이다. */
            ProductSubcategory subcategory,
            long price,
            String description,
            String batchCode) {}
}
