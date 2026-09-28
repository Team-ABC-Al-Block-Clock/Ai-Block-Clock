-- MySQL dump 10.13  Distrib 8.4.11, for Linux (x86_64)
--
-- Host: localhost    Database: ai-block-clock
-- ------------------------------------------------------
-- Server version	8.4.11

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `SPRING_SESSION`
--

DROP TABLE IF EXISTS `SPRING_SESSION`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SPRING_SESSION` (
  `PRIMARY_ID` char(36) NOT NULL,
  `SESSION_ID` char(36) NOT NULL,
  `CREATION_TIME` bigint NOT NULL,
  `LAST_ACCESS_TIME` bigint NOT NULL,
  `MAX_INACTIVE_INTERVAL` int NOT NULL,
  `EXPIRY_TIME` bigint NOT NULL,
  `PRINCIPAL_NAME` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`PRIMARY_ID`),
  UNIQUE KEY `SPRING_SESSION_IX1` (`SESSION_ID`),
  KEY `SPRING_SESSION_IX2` (`EXPIRY_TIME`),
  KEY `SPRING_SESSION_IX3` (`PRINCIPAL_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `SPRING_SESSION`
--

LOCK TABLES `SPRING_SESSION` WRITE;
/*!40000 ALTER TABLE `SPRING_SESSION` DISABLE KEYS */;
INSERT INTO `SPRING_SESSION` VALUES ('0835d120-1bd2-4f46-bd86-ecf8c7a4d2b4','f4e202ee-2546-404f-95ff-8a42c84a8b4f',1790558128778,1790558128778,7200,1790565328778,NULL),('51eab283-1d35-434a-a2d1-ab06101300c5','420121b6-2aae-4fa9-907e-66b41e3d3295',1790559813445,1790560826738,7200,1790568026738,'2'),('74c9f2e3-b80e-43d2-b79e-65f8aecfec30','9f68b5ed-27e2-4b53-9cdf-7b3a5f754aff',1790558128763,1790558128802,7200,1790565328802,NULL),('c2065ec9-0bf3-4a20-b5b3-dc85823bcf23','53aaaeaa-5363-4a04-b4a7-3d2270341322',1790558128781,1790560808566,7200,1790568008566,'4');
/*!40000 ALTER TABLE `SPRING_SESSION` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `SPRING_SESSION_ATTRIBUTES`
--

DROP TABLE IF EXISTS `SPRING_SESSION_ATTRIBUTES`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SPRING_SESSION_ATTRIBUTES` (
  `SESSION_PRIMARY_ID` char(36) NOT NULL,
  `ATTRIBUTE_NAME` varchar(200) NOT NULL,
  `ATTRIBUTE_BYTES` blob NOT NULL,
  PRIMARY KEY (`SESSION_PRIMARY_ID`,`ATTRIBUTE_NAME`),
  CONSTRAINT `SPRING_SESSION_ATTRIBUTES_FK` FOREIGN KEY (`SESSION_PRIMARY_ID`) REFERENCES `SPRING_SESSION` (`PRIMARY_ID`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `SPRING_SESSION_ATTRIBUTES`
--

LOCK TABLES `SPRING_SESSION_ATTRIBUTES` WRITE;
/*!40000 ALTER TABLE `SPRING_SESSION_ATTRIBUTES` DISABLE KEYS */;
INSERT INTO `SPRING_SESSION_ATTRIBUTES` VALUES ('0835d120-1bd2-4f46-bd86-ecf8c7a4d2b4','org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN',_binary '�\�\0sr\06org.springframework.security.web.csrf.DefaultCsrfTokenZ\�\�/��\�\0L\0\nheaderNamet\0Ljava/lang/String;L\0\rparameterNameq\0~\0L\0tokenq\0~\0xpt\0X-CSRF-TOKENt\0_csrft\0$2059c6cf-aafc-4d39-8ca4-625808da15bc'),('51eab283-1d35-434a-a2d1-ab06101300c5','org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN',_binary '�\�\0sr\06org.springframework.security.web.csrf.DefaultCsrfTokenZ\�\�/��\�\0L\0\nheaderNamet\0Ljava/lang/String;L\0\rparameterNameq\0~\0L\0tokenq\0~\0xpt\0X-CSRF-TOKENt\0_csrft\0$e505fa0a-0f54-42c8-8e57-0f991f163fd8'),('51eab283-1d35-434a-a2d1-ab06101300c5','SPRING_SECURITY_CONTEXT',_binary '�\�\0sr\0=org.springframework.security.core.context.SecurityContextImpl\0\0\0\0\0\0l\0L\0authenticationt\02Lorg/springframework/security/core/Authentication;xpsr\0Oorg.springframework.security.authentication.UsernamePasswordAuthenticationToken\0\0\0\0\0\0l\0L\0credentialst\0Ljava/lang/Object;L\0	principalq\0~\0xr\0Gorg.springframework.security.authentication.AbstractAuthenticationTokenӪ(~nGd\0Z\0\rauthenticatedL\0authoritiest\0Ljava/util/Collection;L\0detailsq\0~\0xpsr\0&java.util.Collections$UnmodifiableList�%1�\�\0L\0listt\0Ljava/util/List;xr\0,java.util.Collections$UnmodifiableCollectionB\0�\�^\�\0L\0cq\0~\0xpsr\0java.util.ArrayListx�\��\�a�\0I\0sizexp\0\0\0\0w\0\0\0\0xq\0~\0\rppsr\0#com.aiblockclock.domain.user.UserId\0\0\0\0\0\0\0\0J\0valuexp\0\0\0\0\0\0\0'),('74c9f2e3-b80e-43d2-b79e-65f8aecfec30','org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN',_binary '�\�\0sr\06org.springframework.security.web.csrf.DefaultCsrfTokenZ\�\�/��\�\0L\0\nheaderNamet\0Ljava/lang/String;L\0\rparameterNameq\0~\0L\0tokenq\0~\0xpt\0X-CSRF-TOKENt\0_csrft\0$02fe0cb9-4d35-42d8-8529-2777bea05941'),('c2065ec9-0bf3-4a20-b5b3-dc85823bcf23','org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN',_binary '�\�\0sr\06org.springframework.security.web.csrf.DefaultCsrfTokenZ\�\�/��\�\0L\0\nheaderNamet\0Ljava/lang/String;L\0\rparameterNameq\0~\0L\0tokenq\0~\0xpt\0X-CSRF-TOKENt\0_csrft\0$8669a44d-cfe8-417d-823c-f975bfb1b250'),('c2065ec9-0bf3-4a20-b5b3-dc85823bcf23','SPRING_SECURITY_CONTEXT',_binary '�\�\0sr\0=org.springframework.security.core.context.SecurityContextImpl\0\0\0\0\0\0l\0L\0authenticationt\02Lorg/springframework/security/core/Authentication;xpsr\0Oorg.springframework.security.authentication.UsernamePasswordAuthenticationToken\0\0\0\0\0\0l\0L\0credentialst\0Ljava/lang/Object;L\0	principalq\0~\0xr\0Gorg.springframework.security.authentication.AbstractAuthenticationTokenӪ(~nGd\0Z\0\rauthenticatedL\0authoritiest\0Ljava/util/Collection;L\0detailsq\0~\0xpsr\0&java.util.Collections$UnmodifiableList�%1�\�\0L\0listt\0Ljava/util/List;xr\0,java.util.Collections$UnmodifiableCollectionB\0�\�^\�\0L\0cq\0~\0xpsr\0java.util.ArrayListx�\��\�a�\0I\0sizexp\0\0\0\0w\0\0\0\0xq\0~\0\rppsr\0#com.aiblockclock.domain.user.UserId\0\0\0\0\0\0\0\0J\0valuexp\0\0\0\0\0\0\0');
/*!40000 ALTER TABLE `SPRING_SESSION_ATTRIBUTES` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `agent`
--

DROP TABLE IF EXISTS `agent`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent` (
  `id` varchar(36) NOT NULL,
  `owner_id` bigint NOT NULL,
  `name` varchar(40) NOT NULL,
  `summary` varchar(80) NOT NULL,
  `description` varchar(2000) NOT NULL,
  `usage_example` varchar(500) DEFAULT NULL,
  `onboarding_message` varchar(20) DEFAULT NULL,
  `thumbnail_url` varchar(500) DEFAULT NULL,
  `public_version_id` varchar(36) DEFAULT NULL,
  `public_version_no` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_agent_owner_created` (`owner_id`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `agent`
--

LOCK TABLES `agent` WRITE;
/*!40000 ALTER TABLE `agent` DISABLE KEYS */;
INSERT INTO `agent` VALUES ('225ee29a-527b-4557-8d55-3e2748cdd7b0',4,'SNS 캠페인 기획기','브랜드 전략을 받아 채널·게시 일정·KPI 까지 담은 SNS 캠페인 계획을 만듭니다','브랜드 전략의 포지셔닝과 핵심·보조·일상 메시지를 입력받아 SNS 캠페인을 설계합니다. 인스타그램·유튜브 등 채널과 선정이유, 시기별 게시물 주제·형식, 측정할 KPI를 제안합니다. 광고 헤드라인과 본문 카피는 생성하지 않습니다.','[브랜드 전략 보고서]\n브랜드명: 소소칫솔\n포지셔닝: 교체 시점을 알려 주는 칫솔로 기억되는 브랜드\n핵심 전략: 교체 시점을 눈에 보이게 / 첫 구매 장벽 낮추기 / 대나무 손잡이의 촉감 알리기\n메시지 — 핵심: 바꿀 때를 놓치지 않는 칫솔 / 보조: 색이 빠지면 교체 신호, 3개월마다 알아서 도착합니다 / 일상: 어, 색 빠졌네. 갈 때 됐구나\n핵심 고객: 환경을 신경 쓰지만 가격도 따지는 20~30대 1인 가구\n\n위 보고서로 SNS 캠페인 계획을 세워 주세요.\n\n앞 에이전트를 실제로 돌리셨으면 그 결과의 포지셔닝 · 메시지 · 핵심 전략으로 바꿔 넣으세요. 이어 붙인 티가 훨씬 잘 납니다.\n\n기대 결과: 채널 1~4개(각각 소소칫솔 전략을 근거로 든 이유), 게시물 3~6개(시기 순서), KPI 2~4개(절대 수치 없이 상대 변화로).','브랜드 전략을 붙여 주세요',NULL,'1c5913bb-9336-4460-9bca-64eab747e886',1,'2026-09-27 19:17:45.201980','2026-09-27 19:17:45.201980'),('313ddd93-f8cd-4f78-a4e7-b23685c37fa4',4,'고객 공감 에디터','제품 소개를 보고 고객과 연결한다.','제품 소개를 보고 고객과 연결한다.',NULL,'제품에 대해 소개해주세요.',NULL,'635c4496-3e7b-447d-886f-deeb6f49dcd5',1,'2026-09-27 19:46:37.515367','2026-09-27 19:46:37.515367'),('385b3023-2306-4680-8ad6-f3991b4bb90c',4,'광고 카피 생성기','브랜드 전략의 메시지를 받아 헤드라인·본문 카피·해시태그를 만듭니다','브랜드 전략의 핵심·보조·일상 메시지를 입력받아 광고용 헤드라인, 본문 카피와 해시태그를 만듭니다. 헤드라인은 서로 다른 관점으로 제안하고 근거 없는 최상급 표현과 입력에 없는 제품 기능은 피합니다. 채널과 게시 일정은 정하지 않습니다.','[브랜드 전략 보고서]\n브랜드명: 소소칫솔\n포지셔닝: 교체 시점을 알려 주는 칫솔로 기억되는 브랜드\n핵심 전략: 교체 시점을 눈에 보이게 / 첫 구매 장벽 낮추기 / 대나무 손잡이의 촉감 알리기\n메시지 — 핵심: 바꿀 때를 놓치지 않는 칫솔 / 보조: 색이 빠지면 교체 신호, 3개월마다 알아서 도착합니다 / 일상: 어, 색 빠졌네. 갈 때 됐구나\n핵심 고객: 환경을 신경 쓰지만 가격도 따지는 20~30대 1인 가구\n\n위 보고서를 바탕으로 광고 카피를 작성해 주세요.\n\n기대 결과: 헤드라인 3~5개가 서로 다른 축에서 나오고, 금칙어가 한 건도 없고, 해시태그에 #소소칫솔 이 들어 있는 것. 결과 화면에서 \"최고\" \"100%\" \"1위\" 를 눈으로 훑어 보세요 — 하나라도 있으면 그 판은 시연에 못 씁니다.\n\n프롬프트에 사진 관련 지침이 있었는데, 실행 화면이 엑셀·CSV 만 받아 사진을 못 붙이므로 그 부분은 뺐습니다.','브랜드 메시지를 알려 주세요',NULL,'6dfcdb77-48ce-4072-af98-c8e059a1b38a',1,'2026-09-27 19:24:12.827401','2026-09-27 19:24:12.827401'),('689bed13-64b2-4988-8cfe-d59beac85ce5',4,'제품 강점 에디터','제품에 대해 강점을 설명합니다.','제품에 대한 강점을 설명.',NULL,'제품에 대해 알려주세요',NULL,'18fdf2f8-4cd9-440a-8445-3b13e645e97c',1,'2026-09-27 19:43:38.675495','2026-09-27 19:43:38.675495'),('788477e9-33c2-4bae-9b10-7edab198048f',4,'브랜드 말투 에디터','글을 읽고 브랜드 친화적으로 적어줍니다.','친근하고 기억하기 쉬운 표현으로 변경한다.',NULL,'글을 적어주세요',NULL,'677041de-753c-4783-ab9a-71b092651884',1,'2026-09-27 19:49:04.068993','2026-09-27 19:49:04.068993'),('d26b84a4-558e-4694-b8f8-ae629d4d0a23',2,'제품 분석','상품을 분석해드립니다.','입력하신 상품을 분석해드립니다. 기획, 홍보 등 다양한 분야에서 활용하실 수 있습니다.','최근 휴지 시장의 현황을 분석해줘.','어떤 제품을 분석해드릴까요?',NULL,'a48deb68-1f9a-4169-981d-0ca8b35fb27d',1,'2026-09-28 00:30:58.675422','2026-09-28 00:34:53.115027'),('ee9a8750-f0ce-4a2a-b46d-db40a71862f3',4,'브랜딩 전략 수립기','신제품 브리프로 경쟁 강도를 판정하고 포지셔닝·핵심 전략·메시지까지 담은 브랜드 전략 보고서를 만듭니다','신제품의 브랜드명·제품 카테고리·핵심 고객·가격대·차별점을 입력받아 경쟁 강도를 판정합니다. 제품 강점과 약점에 근거한 포지셔닝, 핵심 전략, 핵심·보조·일상 메시지를 포함한 브랜드 전략을 만듭니다. 실시간 웹 조사는 하지 않으므로 시장 정보는 별도 확인이 필요합니다.','브랜드명: 소소칫솔 / 제품 카테고리: 칫솔 / 핵심 고객: 환경을 신경 쓰지만 가격도 따지는 20~30대 1인 가구 / 가격대: 중가 / 차별점: 미세모 밀도 2배, 3개월마다 자동 배송되는 교체 구독, 대나무 손잡이\n\n두피온으로 돌렸다면 입력 예시도 두피온 것으로 바꿔 주세요','어떤 제품을 브랜딩할까요?',NULL,'6507a395-7842-4ca8-a610-1e53105fd8ad',1,'2026-09-27 18:51:17.150091','2026-09-27 18:51:17.150091'),('fd6db8f4-9d26-4be1-be5b-c94e854afbae',2,'대중 공략 전략기','대중을 공략할 수 있는 전략을 세워드립니다.','브랜드 마케팅에 필수인 대중 공략 전략을 추천해드립니다. 제 전략을 사용하시면 대중들의 마음을 모두 사로잡으실 수 있습니다.','대중의 마음을 사로잡으려면 어떻게 해야 할지 알려줘.','무엇을 도와드릴까요?',NULL,'62afbe71-c808-4db0-b83b-f87d56f281ee',1,'2026-09-28 00:37:05.930427','2026-09-28 00:37:05.930427');
/*!40000 ALTER TABLE `agent` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `agent_favorite`
--

DROP TABLE IF EXISTS `agent_favorite`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `agent_id` varchar(36) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_agent_favorite_user_agent` (`user_id`,`agent_id`),
  KEY `idx_agent_favorite_user_created` (`user_id`,`created_at`,`id`),
  KEY `idx_agent_favorite_agent` (`agent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `agent_favorite`
--

LOCK TABLES `agent_favorite` WRITE;
/*!40000 ALTER TABLE `agent_favorite` DISABLE KEYS */;
INSERT INTO `agent_favorite` VALUES (1,4,'fd6db8f4-9d26-4be1-be5b-c94e854afbae','2026-09-28 00:43:44.776277'),(2,4,'d26b84a4-558e-4694-b8f8-ae629d4d0a23','2026-09-28 00:43:48.861944'),(3,2,'788477e9-33c2-4bae-9b10-7edab198048f','2026-09-28 01:19:09.048220'),(4,2,'225ee29a-527b-4557-8d55-3e2748cdd7b0','2026-09-28 01:19:13.418977'),(5,2,'313ddd93-f8cd-4f78-a4e7-b23685c37fa4','2026-09-28 01:19:17.318441');
/*!40000 ALTER TABLE `agent_favorite` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `agent_grade_history`
--

DROP TABLE IF EXISTS `agent_grade_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_grade_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `agent_id` varchar(36) NOT NULL,
  `successful_runs` bigint NOT NULL,
  `supplier_failures` bigint NOT NULL,
  `review_count` int NOT NULL,
  `average_rating` decimal(3,2) DEFAULT NULL,
  `measured_at` datetime(6) DEFAULT NULL,
  `rating_score` decimal(9,6) NOT NULL,
  `usage_score` decimal(9,6) NOT NULL,
  `success_rate` decimal(7,6) NOT NULL,
  `score` decimal(7,4) NOT NULL,
  `score_tier` varchar(20) NOT NULL,
  `tier` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_agent_grade_history_agent` (`agent_id`,`id`),
  CONSTRAINT `ck_agent_grade_history_counts` CHECK (((`successful_runs` >= 0) and (`supplier_failures` >= 0) and (`review_count` >= 0))),
  CONSTRAINT `ck_agent_grade_history_rating` CHECK ((((`review_count` = 0) and (`average_rating` is null)) or ((`review_count` > 0) and (`average_rating` is not null) and (`average_rating` between 1 and 5)))),
  CONSTRAINT `ck_agent_grade_history_scores` CHECK (((`rating_score` between 0 and 100) and (`usage_score` between 0 and 100) and (`success_rate` between 0 and 1) and (`score` between 0 and 100)))
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `agent_grade_history`
--

LOCK TABLES `agent_grade_history` WRITE;
/*!40000 ALTER TABLE `agent_grade_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `agent_grade_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `agent_grade_metric`
--

DROP TABLE IF EXISTS `agent_grade_metric`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_grade_metric` (
  `agent_id` varchar(36) NOT NULL,
  `successful_runs` bigint NOT NULL,
  `supplier_failures` bigint NOT NULL,
  `review_count` int NOT NULL,
  `average_rating` decimal(3,2) DEFAULT NULL,
  `measured_at` datetime(6) NOT NULL,
  PRIMARY KEY (`agent_id`),
  CONSTRAINT `ck_agent_grade_metric_counts` CHECK (((`successful_runs` >= 0) and (`supplier_failures` >= 0) and (`review_count` >= 0))),
  CONSTRAINT `ck_agent_grade_metric_rating` CHECK ((((`review_count` = 0) and (`average_rating` is null)) or ((`review_count` > 0) and (`average_rating` is not null) and (`average_rating` between 1 and 5))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `agent_grade_metric`
--

LOCK TABLES `agent_grade_metric` WRITE;
/*!40000 ALTER TABLE `agent_grade_metric` DISABLE KEYS */;
INSERT INTO `agent_grade_metric` VALUES ('225ee29a-527b-4557-8d55-3e2748cdd7b0',2,0,0,NULL,'2026-09-28 01:34:46.274675'),('313ddd93-f8cd-4f78-a4e7-b23685c37fa4',5,0,0,NULL,'2026-09-28 01:36:17.443173'),('689bed13-64b2-4988-8cfe-d59beac85ce5',4,0,0,NULL,'2026-09-28 01:30:37.984651'),('788477e9-33c2-4bae-9b10-7edab198048f',5,0,0,NULL,'2026-09-28 01:36:17.443173'),('d26b84a4-558e-4694-b8f8-ae629d4d0a23',3,0,0,NULL,'2026-09-28 01:47:16.684798'),('ee9a8750-f0ce-4a2a-b46d-db40a71862f3',1,0,0,NULL,'2026-09-28 01:33:38.613867');
/*!40000 ALTER TABLE `agent_grade_metric` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `agent_provenance`
--

DROP TABLE IF EXISTS `agent_provenance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_provenance` (
  `agent_version_id` varchar(36) NOT NULL,
  `agent_id` varchar(36) NOT NULL,
  `version_no` int NOT NULL,
  `document_name` varchar(31) NOT NULL,
  `status` varchar(20) NOT NULL,
  `requested_at` datetime(6) NOT NULL,
  `last_attempted_at` datetime(6) DEFAULT NULL,
  `owner_wallet_address` varchar(42) DEFAULT NULL,
  `metadata_hash` varchar(66) DEFAULT NULL,
  `document_uri` varchar(200) DEFAULT NULL,
  `contract_address` varchar(42) DEFAULT NULL,
  `transaction_hash` varchar(66) DEFAULT NULL,
  `block_number` bigint DEFAULT NULL,
  `confirmed_at` datetime(6) DEFAULT NULL,
  `integrity_result` varchar(20) DEFAULT NULL,
  `integrity_mismatch_reason` varchar(30) DEFAULT NULL,
  `integrity_checked_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`agent_version_id`),
  UNIQUE KEY `uk_agent_provenance_document_name` (`document_name`),
  KEY `idx_agent_provenance_agent_version` (`agent_id`,`version_no`),
  KEY `idx_agent_provenance_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `agent_provenance`
--

LOCK TABLES `agent_provenance` WRITE;
/*!40000 ALTER TABLE `agent_provenance` DISABLE KEYS */;
INSERT INTO `agent_provenance` VALUES ('18fdf2f8-4cd9-440a-8445-3b13e645e97c','689bed13-64b2-4988-8cfe-d59beac85ce5',1,'aJvtE2SySYiM_tWb6shc5Q-v1','CONFIRMED','2026-09-27 19:43:38.680606','2026-09-27 19:43:55.830947','0xde75a3c96fae7be1132f8cddff30c10ea4204e58','0x91fe8e89b99be227ce6f83dd78a27a5b9fc65fd9803b5ebb956451b3e1c2f849','abc://agents/689bed13-64b2-4988-8cfe-d59beac85ce5/versions/1?owner=0xde75a3c96fae7be1132f8cddff30c10ea4204e58','0xBBC46459B7A364089A924cf5C091e6CD9B5AB934','0x05a234317d86359b18d126a185a08f2af66d5ea3d62bc5e6805c93aa887071c2',11386526,'2026-09-27 19:43:55.830947','MATCHED',NULL,'2026-09-28 00:10:48.424761'),('1c5913bb-9336-4460-9bca-64eab747e886','225ee29a-527b-4557-8d55-3e2748cdd7b0',1,'Il7imlJ7RVeNVT4nSM3XsA-v1','CONFIRMED','2026-09-27 19:17:45.208061','2026-09-27 19:17:56.195263','0xde75a3c96fae7be1132f8cddff30c10ea4204e58','0x1c9189d1d3f6193ad191279d6c0a430afcab0605e43d76d1d3d47cceceab9ce6','abc://agents/225ee29a-527b-4557-8d55-3e2748cdd7b0/versions/1?owner=0xde75a3c96fae7be1132f8cddff30c10ea4204e58','0xBBC46459B7A364089A924cf5C091e6CD9B5AB934','0x77298d748df9b032efb585260aa7e60487b9b82cf36dc0dcdfb85eaafe87c713',11386370,'2026-09-27 19:17:56.195263','MATCHED',NULL,'2026-09-28 01:19:12.644960'),('62afbe71-c808-4db0-b83b-f87d56f281ee','fd6db8f4-9d26-4be1-be5b-c94e854afbae',1,'_W249J0mS-G-W8lOhUr7rg-v1','CONFIRMED','2026-09-28 00:37:05.935285','2026-09-28 00:37:25.598883','0x2e1a8f2741c16167796870dfe674ff437c188060','0x6d7becd20f10c70a5b3508526da2c916eaf85c86c0a60d242ad02ccb1d30211a','abc://agents/fd6db8f4-9d26-4be1-be5b-c94e854afbae/versions/1?owner=0x2e1a8f2741c16167796870dfe674ff437c188060','0xBBC46459B7A364089A924cf5C091e6CD9B5AB934','0xd2ac1dd60fec1b3e06e15b5e6461d7cec1abb327a764ffe7f466ee67961798f6',11388287,'2026-09-28 00:37:25.598883','MATCHED',NULL,'2026-09-28 01:44:06.808098'),('635c4496-3e7b-447d-886f-deeb6f49dcd5','313ddd93-f8cd-4f78-a4e7-b23685c37fa4',1,'MT3dk_jNT3ik57I2hcN_pA-v1','CONFIRMED','2026-09-27 19:46:37.520430','2026-09-27 19:46:45.970055','0xde75a3c96fae7be1132f8cddff30c10ea4204e58','0x3c48f9fb90100d93ec7e2872239d05bcc3f7f1ac28c0977cd2889d4ff06b29b3','abc://agents/313ddd93-f8cd-4f78-a4e7-b23685c37fa4/versions/1?owner=0xde75a3c96fae7be1132f8cddff30c10ea4204e58','0xBBC46459B7A364089A924cf5C091e6CD9B5AB934','0xbc78598d10750a0434999510983de30414345abc4c6c421c62bcb0e037d2e8cb',11386543,'2026-09-27 19:46:45.970055','MATCHED',NULL,'2026-09-28 01:19:16.558039'),('6507a395-7842-4ca8-a610-1e53105fd8ad','ee9a8750-f0ce-4a2a-b46d-db40a71862f3',1,'7pqHUPDOSiq0bdtApxhi8w-v1','CONFIRMED','2026-09-27 18:51:17.188708','2026-09-27 18:51:35.769435','0xde75a3c96fae7be1132f8cddff30c10ea4204e58','0x118e0981f59aa093516136a7f15f47d1e3e31d218575bc3c2d18ae6f99d4fce1','abc://agents/ee9a8750-f0ce-4a2a-b46d-db40a71862f3/versions/1?owner=0xde75a3c96fae7be1132f8cddff30c10ea4204e58','0xBBC46459B7A364089A924cf5C091e6CD9B5AB934','0x09807da11cb9b261df5fbfff3d6739c5b3ccd5adc1f4a7b60786c2a0431da891',11386212,'2026-09-27 18:51:35.769435','MATCHED',NULL,'2026-09-27 20:09:47.354640'),('677041de-753c-4783-ab9a-71b092651884','788477e9-33c2-4bae-9b10-7edab198048f',1,'eIR36TPCS66bEH7asZgEjw-v1','CONFIRMED','2026-09-27 19:49:04.074021','2026-09-27 19:49:26.137567','0xde75a3c96fae7be1132f8cddff30c10ea4204e58','0x6ff4b5d7c5627a94695fe37fa4a20986ff5e527bbe529807b21b521d674b7f48','abc://agents/788477e9-33c2-4bae-9b10-7edab198048f/versions/1?owner=0xde75a3c96fae7be1132f8cddff30c10ea4204e58','0xBBC46459B7A364089A924cf5C091e6CD9B5AB934','0x3b91375c630cf49695d8212e5ffb6148bc9f4fb9f1abfc487d416d6b94fe1ab8',11386559,'2026-09-27 19:49:26.137567','MATCHED',NULL,'2026-09-28 01:19:08.188305'),('6dfcdb77-48ce-4072-af98-c8e059a1b38a','385b3023-2306-4680-8ad6-f3991b4bb90c',1,'OFswIyMGRoCK1vOZG0u5DA-v1','CONFIRMED','2026-09-27 19:24:12.843187','2026-09-27 19:24:36.395390','0xde75a3c96fae7be1132f8cddff30c10ea4204e58','0x4a3c436e95615bcee43258f9325fbd26b9f74cc5fddc0181103c60675a5ed93f','abc://agents/385b3023-2306-4680-8ad6-f3991b4bb90c/versions/1?owner=0xde75a3c96fae7be1132f8cddff30c10ea4204e58','0xBBC46459B7A364089A924cf5C091e6CD9B5AB934','0xf2d35ee3a5062970c3870042f8616a6d90573e4a0b8b5869d340ccb631111141',11386410,'2026-09-27 19:24:36.395390','MATCHED',NULL,'2026-09-28 00:10:42.765969'),('a48deb68-1f9a-4169-981d-0ca8b35fb27d','d26b84a4-558e-4694-b8f8-ae629d4d0a23',1,'0muEpFWORpS4-K5inU0KIw-v1','CONFIRMED','2026-09-28 00:30:58.680061','2026-09-28 00:31:16.427778','0x2e1a8f2741c16167796870dfe674ff437c188060','0xcb90ec9aaafa96a9255ee46575b6946cf25db4f6f496f56af392f8de28168a83','abc://agents/d26b84a4-558e-4694-b8f8-ae629d4d0a23/versions/1?owner=0x2e1a8f2741c16167796870dfe674ff437c188060','0xBBC46459B7A364089A924cf5C091e6CD9B5AB934','0x59b8ec492a3119137aa1cab6dbe4f270d9838fc5d6a6474fe37bd4bc476b7964',11388250,'2026-09-28 00:31:16.427778','MATCHED',NULL,'2026-09-28 00:43:47.903634');
/*!40000 ALTER TABLE `agent_provenance` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `agent_tier_change`
--

DROP TABLE IF EXISTS `agent_tier_change`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_tier_change` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `agent_id` varchar(36) NOT NULL,
  `agent_version_id` varchar(36) NOT NULL,
  `grade_history_id` bigint NOT NULL,
  `before_tier` varchar(20) NOT NULL,
  `after_tier` varchar(20) NOT NULL,
  `before_price` decimal(41,6) NOT NULL,
  `after_price` decimal(41,6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `ck_agent_tier_change_changed` CHECK ((`before_tier` <> `after_tier`)),
  CONSTRAINT `ck_agent_tier_change_prices` CHECK (((`before_price` > 0) and (`after_price` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `agent_tier_change`
--

LOCK TABLES `agent_tier_change` WRITE;
/*!40000 ALTER TABLE `agent_tier_change` DISABLE KEYS */;
/*!40000 ALTER TABLE `agent_tier_change` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `agent_version`
--

DROP TABLE IF EXISTS `agent_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_version` (
  `id` varchar(36) NOT NULL,
  `agent_id` varchar(36) NOT NULL,
  `version_no` int NOT NULL,
  `graph_id` varchar(36) NOT NULL,
  `source_workflow_id` varchar(36) NOT NULL,
  `source_revision` bigint NOT NULL,
  `prompt_node_count` int NOT NULL,
  `base_cost` decimal(41,6) NOT NULL,
  `tier` varchar(20) NOT NULL,
  `publication_state` varchar(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_version_no` (`agent_id`,`version_no`),
  CONSTRAINT `fk_agent_version_agent` FOREIGN KEY (`agent_id`) REFERENCES `agent` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `agent_version`
--

LOCK TABLES `agent_version` WRITE;
/*!40000 ALTER TABLE `agent_version` DISABLE KEYS */;
INSERT INTO `agent_version` VALUES ('18fdf2f8-4cd9-440a-8445-3b13e645e97c','689bed13-64b2-4988-8cfe-d59beac85ce5',1,'c76d6191-0e3f-4ccd-a6a1-e63b0f5984cb','30078b76-e5cf-4709-a37d-f0ca95e5264e',1,1,0.013500,'TIER_4','PUBLISHED','2026-09-27 19:43:38.676581','2026-09-27 19:43:38.676581'),('1c5913bb-9336-4460-9bca-64eab747e886','225ee29a-527b-4557-8d55-3e2748cdd7b0',1,'5d9915d1-c4fc-4c55-bc7a-c57f0718be55','7e4a8f5e-06c0-4b63-897d-05e4b83aabb5',2,1,0.013500,'TIER_4','PUBLISHED','2026-09-27 19:17:45.203080','2026-09-27 19:17:45.203080'),('62afbe71-c808-4db0-b83b-f87d56f281ee','fd6db8f4-9d26-4be1-be5b-c94e854afbae',1,'1507d9fb-99f8-41ba-9bf5-af02c350f276','d2afd46d-0c93-47ea-9d30-17172bd0d913',1,1,0.013500,'TIER_4','PUBLISHED','2026-09-28 00:37:05.931299','2026-09-28 00:37:05.931299'),('635c4496-3e7b-447d-886f-deeb6f49dcd5','313ddd93-f8cd-4f78-a4e7-b23685c37fa4',1,'748fcbd2-f9c6-40e7-a925-6d9351d23543','3486d1bb-79ff-40c0-a9bc-9dd8a906cef9',2,1,0.013500,'TIER_4','PUBLISHED','2026-09-27 19:46:37.516337','2026-09-27 19:46:37.516337'),('6507a395-7842-4ca8-a610-1e53105fd8ad','ee9a8750-f0ce-4a2a-b46d-db40a71862f3',1,'c45be2ea-0095-4483-b2a3-897b01fe21e6','ff4893e6-25bf-477a-ba8b-c7770ada3b1b',2,5,0.067500,'TIER_4','PUBLISHED','2026-09-27 18:51:17.156750','2026-09-27 18:51:17.156750'),('677041de-753c-4783-ab9a-71b092651884','788477e9-33c2-4bae-9b10-7edab198048f',1,'8f2e7beb-952d-4891-847a-9616b6dfd74a','5052cee1-9e23-4be4-97e8-f76afdac468f',2,1,0.013500,'TIER_4','PUBLISHED','2026-09-27 19:49:04.069995','2026-09-27 19:49:04.069995'),('6dfcdb77-48ce-4072-af98-c8e059a1b38a','385b3023-2306-4680-8ad6-f3991b4bb90c',1,'ce3074e0-7fdb-472e-84dc-a07f985d0be6','4aa70c6b-7878-4ae1-9ae5-fc3ee3befe3b',2,1,0.013500,'TIER_4','PUBLISHED','2026-09-27 19:24:12.830759','2026-09-27 19:24:12.830759'),('a48deb68-1f9a-4169-981d-0ca8b35fb27d','d26b84a4-558e-4694-b8f8-ae629d4d0a23',1,'3701e8c4-7b3a-466d-98d3-458498c64b56','8795d8c3-c1fc-4814-9c99-9f07ed2bd3d7',2,1,0.013500,'TIER_4','PUBLISHED','2026-09-28 00:30:58.676292','2026-09-28 00:30:58.676292');
/*!40000 ALTER TABLE `agent_version` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `agent_version_node`
--

DROP TABLE IF EXISTS `agent_version_node`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_version_node` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `agent_version_id` varchar(36) NOT NULL,
  `node_key` varchar(64) NOT NULL,
  `owner_id` bigint NOT NULL,
  `name` varchar(40) NOT NULL,
  `node_type` varchar(20) NOT NULL,
  `prompt` text NOT NULL,
  `model` varchar(40) NOT NULL,
  `output_kind` varchar(10) NOT NULL DEFAULT 'TEXT',
  `output_example` text,
  `base_cost` decimal(41,6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_version_node_key` (`agent_version_id`,`node_key`),
  CONSTRAINT `fk_agent_version_node_version` FOREIGN KEY (`agent_version_id`) REFERENCES `agent_version` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `agent_version_node`
--

LOCK TABLES `agent_version_node` WRITE;
/*!40000 ALTER TABLE `agent_version_node` DISABLE KEYS */;
INSERT INTO `agent_version_node` VALUES (11,'6507a395-7842-4ca8-a610-1e53105fd8ad','n2',4,'시장 조사','LLM','당신은 소비재 브랜드 컨설턴트다. 브랜드 기획 요청서를 받아 시장 조사 결과를 작성한다.\n\n[요청서 위치]\n브랜드 기획 요청서는 아래 「사용자 코멘트」 절에 있다. 그 요청서로 시장 조사 결과를 작성한다.\n\n[작성 원칙]\n- 요청서에 적힌 카테고리의 한국 시장을 기준으로 쓴다. 해외 시장은 다루지 않는다.\n- 실재하는 브랜드만 경쟁사로 든다. 이름이 확실하지 않으면 그 브랜드는 빼고 다른 것을 든다.\n- 숫자를 지어내지 않는다. 점유율·매출·성장률처럼 확인할 수 없는 수치는 쓰지 않는다.\n  규모를 말해야 할 때는 \"대형마트 진열 기준 상위권\" 처럼 확인 가능한 표현을 쓴다.\n- 요청서의 \'추가 요청\'에 하지 말라고 적힌 것은 시장 개요와 트렌드에서도 지킨다.\n- 참고 자료의 체크리스트를 판정 기준으로 삼는다.\n\n[competition_level 판정 — 반드시 이 순서로 한다]\n1단계. competitors 를 먼저 채운다. 그 카테고리 상품이 한국의 오프라인 일반 매장\n       (대형마트·드럭스토어·편의점·생활용품점)의 진열대에 놓이는 \'전국 유통 브랜드\'만 적는다.\n       - 온라인이나 전문점(스포츠·등산·클라이밍·캠핑 전문점, 전문 쇼핑몰)에서만 파는\n         브랜드는 적지 않는다. 온라인에서 살 수 있다는 것만으로는 전국 유통이 아니다.\n       - 해외 브랜드는 위 일반 매장 진열대에서 흔히 보일 때만 적는다.\n       - 해외 직구로만 구할 수 있는 브랜드는 적지 않는다.\n       - 특정 지역·소수 매장에서만 파는 브랜드는 적지 않는다.\n       - 요청서의 브랜드(신규 진입자)는 적지 않는다.\n       ★ 칸을 채우려고 만들어 내지 않는다. 실제로 떠오르는 것이 2개뿐이면 2개만 적는다.\n         확실하지 않은 이름을 넣느니 적게 적는 것이 맞다.\n2단계. N = 1단계에서 적은 competitors 의 개수. competitors 는 최대 5개까지만 적는다.\n       - N 이 5 이상이면 competition_level 은 \"high\"\n       - N 이 4 이하이면 competition_level 은 \"low\"\n3단계. level_reason 의 첫 문장을 반드시 \"전국 유통 브랜드 N개를 확인했다.\" 로 시작한다.\n       ★ 이 N 은 competitors 배열의 개수와 반드시 같아야 한다. 다르면 틀린 출력이다.\n       그 뒤에 한 문장으로 근거를 덧붙인다.\n\n이 판정은 개인적인 인상이 아니라 competitors 를 센 결과다.\n같은 요청서에는 항상 같은 값이 나와야 한다.\n\n[각 항목 작성 지침]\n- market_overview_md: 이 카테고리를 모르는 사람이 읽어도 상황이 그려지도록 3~5문장.\n  누가 무엇 때문에 사는지, 최근 무엇이 달라지고 있는지를 담는다. 마크다운 허용.\n- competitors: 1단계에서 센 브랜드 중 요청서의 제품과 가장 많이 겹치는 순서로 2~5개.\n  positioning 은 그 브랜드가 내세우는 한마디, note 는 우리와 부딪히는 지점.\n- trends: 카테고리 전반의 흐름 2~5개. 한 줄짜리 명사구로 쓴다.\n- competition_level / level_reason: 위 판정 절차를 그대로 따른다.\n[참고 — level_reason 작성 예]\n\n전국 유통 브랜드 7개를 확인했다. 대형마트 구강용품 진열대가 이미 기성 브랜드로 채워져 있어\n신규 브랜드가 진열 공간을 확보하기 어렵다.\n\n전국 유통 브랜드 2개를 확인했다. 대부분 소형 브랜드와 해외 직구로 유통되고 있어\n일반 소비자가 매장에서 비교 구매할 수 있는 선택지가 적다.\n\n첫 문장의 형식(전국 유통 브랜드 N개를 확인했다)은 그대로 지키고, 뒤 문장은 카테고리에 맞게 쓴다.\n\n[참고 — competitors 를 억지로 채우지 않는다]\n\n경쟁사가 적은 카테고리에서는 2~3개만 적는 것이 정상이다. 그때 N 은 그 개수 그대로다.\n\n  competitors: 3개를 적었다  →  N = 3  →  competition_level = \"low\"\n               level_reason: \"전국 유통 브랜드 3개를 확인했다. …\"\n\n5칸을 채우려고 들어본 적 없는 이름이나 해외 직구 브랜드를 넣으면 판정이 뒤집힌다.\n적은 개수가 곧 판정이므로, 확실한 것만 적는 것이 가장 중요하다.','GEMINI_2_5_PRO','JSON','{\"market_overview_md\":\"샘플 시장 개요\",\"competitors\":[{\"name\":\"샘플 경쟁사명\",\"positioning\":\"샘플 포지션\",\"note\":\"샘플 특징\"},{\"name\":\"샘플 경쟁사명\",\"positioning\":\"샘플 포지션\",\"note\":\"샘플 특징\"}],\"trends\":[{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"}],\"competition_level\":\"high\",\"level_reason\":\"샘플 판정 근거\"}',0.013500),(12,'6507a395-7842-4ca8-a610-1e53105fd8ad','n3',4,'제품 분석','LLM','당신은 소비재 제품 기획자다. 브랜드 기획 요청서를 받아 제품 분석 결과를 작성한다.\n\n[입력 위치]\n브랜드 기획 요청서는 아래 「사용자 코멘트」 절에 있다. 그 요청서로 제품 분석 결과를 작성한다.\n앞 단계의 시장 조사 결과는 아래 「입력」 절에 있다. 그것은 market_brief 로 옮겨 적기만 하고,\nstrengths·weaknesses·opportunities 를 쓸 때는 보지 않는다.\n\n[이 분석의 범위]\n- 요청서에 적힌 제품 하나만 본다. 시장 상황·경쟁사·업계 트렌드는 다루지 않는다.\n- \"경쟁사 대비\", \"업계 평균보다\" 같은 비교 표현을 쓰지 않는다.\n- 숫자를 지어내지 않는다. 요청서에 있는 수치만 쓴다.\n\n[각 항목 작성 지침]\n- market_brief: 「입력」 절 시장 조사 결과에서 경쟁 강도 판정(competition_level)과 그 근거,\n  경쟁사 이름, 주요 트렌드를 3~4문장으로 옮겨 적는다. 해석하거나 덧붙이지 않는다.\n\n- strengths: 제품이 실제로 잘하는 것 2~5개.\n  label 은 5~15자 명사구, detail 은 한 문장.\n  요청서의 \'차별점\'을 그대로 옮겨 적지 말고, 그것이 고객에게 무엇을 해 주는지로 바꿔 쓴다.\n\n- weaknesses: 2~5개. 고객이 구매를 망설일 이유를 쓴다.\n  우리 사정(원가·생산 난이도)이 아니라 고객이 느낄 불편·불안·부담으로 쓴다.\n  좋게 들리게 다듬지 않는다. \"장점의 이면\" 형태로 쓰면 약점이 아니다.\n  예를 들어 가격이 높다면 \"왜 비싼지 설명이 필요하다\"가 아니라\n  \"같은 쓰임의 더 싼 제품이 있는데 굳이 이걸 살 이유를 모른다\"로 쓴다.\n\n- opportunities: 2~5개. 약점을 덮거나 강점을 키울 여지.\n  label 만 있는 한 줄짜리 항목이다. 짧은 명사구로 쓴다.\n\n[요청서의 \'추가 요청\']\n하지 말라고 적힌 것은 여기서도 지킨다. 표현 방식에 대한 요청이면 문장에 반영한다.\n\n[참고 — weaknesses 를 쓰는 방식]\n\n이렇게 쓰지 않는다 (약점처럼 생긴 칭찬):\n  · \"친환경 소재를 써서 원가가 다소 높습니다\"\n  · \"기능이 많아 처음에는 익숙해지는 시간이 필요합니다\"\n\n이렇게 쓴다 (고객이 망설이는 지점):\n  · label: 가격 대비 체감 어려움\n    detail: 같은 값에 대용량 제품을 살 수 있는데, 차이를 써 보기 전에는 알 수 없다.\n  · label: 교체 시점을 놓침\n    detail: 언제 갈아야 하는지 눈으로 표시되지 않아 계속 쓰게 된다.','GEMINI_2_5_PRO','JSON','{\"market_brief\":\"샘플 시장 요약\",\"strengths\":[{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"}],\"weaknesses\":[{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"}],\"opportunities\":[{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"}]}',0.013500),(13,'6507a395-7842-4ca8-a610-1e53105fd8ad','n5',4,'차별화 전략','LLM','당신은 브랜드 전략가다. 제품 분석 결과를 받아 전략 초안을 작성한다.\n\n[입력 위치]\n아래 「입력」 절에 제품 분석 결과가 있다. 그 안의 market_brief 가 시장 조사 요약이다\n(경쟁 강도 판정 · 경쟁사 · 트렌드). 브랜드 기획 요청서는 「사용자 코멘트」 절에 있다.\n이 셋을 함께 보고 차별화 전략 초안을 작성한다.\n\n[전제 — 바꾸지 않는다]\n이 요청은 경쟁이 치열한 카테고리로 판정되어 배정됐다.\n따라서 approach 는 반드시 \"차별화\" 다. 내용을 보고 다시 판단하지 않는다.\n\n[차별화 전략이란 무엇인가]\n모두와 같은 말을 더 크게 하는 대신, 경쟁사가 비어 둔 자리를 찾아 거기에 선다.\n- 이미 여러 브랜드가 말하고 있는 것은 우리의 중심 메시지로 쓰지 않는다.\n- 제품 분석의 약점을 숨기지 않는다. 비켜 가거나, 그 약점이 문제가 되지 않는\n  고객층으로 범위를 좁힌다.\n- 시장 전체를 노리지 않는다. 좁혀서 이기는 그림을 그린다.\n\n[각 항목 작성 지침]\n- approach: \"차별화\" 로 고정한다.\n\n- positioning: 한 문장. \"누구에게 무엇으로 기억되는 브랜드인가\"를 담는다.\n  market_brief 에 적힌 경쟁사와 그들이 차지한 자리를 먼저 훑고,\n  그중 어느 것과도 겹치지 않는 자리를 잡는다.\n\n- rationale_md: 왜 이 자리인지 3~5문장. 마크다운 허용.\n  반드시 두 가지를 근거로 든다.\n    (1) market_brief 에서 — 어느 경쟁사가 무엇을 이미 차지하고 있어서 그 자리를 피했는지\n        (경쟁사 이름을 최소 하나 적는다)\n    (2) 제품 분석에서 — 우리 강점 중 무엇이 이 자리를 뒷받침하는지\n  약점을 하나 골라 어떻게 다룰지도 한 문장으로 적는다.\n\n- core_strategies: 3~5개. label 은 5~15자 명사구, detail 은 실행 단위로 한 문장.\n  \"브랜드 인지도를 높인다\" 처럼 누구에게나 해당하는 문장은 쓰지 않는다.\n  무엇을 하겠다는 것인지 읽고 바로 그림이 그려져야 한다.\n\n[하지 않을 것]\n- 숫자를 지어내지 않는다. 받은 입력에 없는 수치는 쓰지 않는다.\n- 경쟁사를 깎아내리지 않는다. 자리를 구분하는 것이지 우열을 매기는 게 아니다.\n- 채널·예산·일정은 여기서 정하지 않는다.\n\n[참고 — core_strategies 를 쓰는 방식]\n\n이렇게 쓰지 않는다 (누구에게나 해당하는 말):\n  · label: 브랜드 인지도 제고    detail: SNS 를 활용해 인지도를 높인다.\n  · label: 품질 강조             detail: 우수한 품질을 알린다.\n\n이렇게 쓴다 (읽으면 그림이 그려지는 말):\n  · label: 교체 시점을 눈에 보이게\n    detail: 솔에 색이 빠지는 지표를 넣어, 교체 구독의 이유를 설명 없이 보여 준다.\n  · label: 첫 구매 장벽 낮추기\n    detail: 첫 달은 단품 가격으로 시작하게 해서 구독 결심을 뒤로 미룬다.','GEMINI_2_5_PRO','JSON','{\"approach\":\"차별화\",\"positioning\":\"샘플 포지셔닝\",\"rationale_md\":\"샘플 근거\",\"core_strategies\":[{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"}]}',0.013500),(14,'6507a395-7842-4ca8-a610-1e53105fd8ad','n6',4,'대중 공략 전략','LLM','당신은 브랜드 전략가다. 제품 분석 결과를 받아 전략 초안을 작성한다.\n\n[입력 위치]\n아래 「입력」 절에 제품 분석 결과가 있다. 그 안의 market_brief 가 시장 조사 요약이다\n(경쟁 강도 판정 · 경쟁사 · 트렌드). 브랜드 기획 요청서는 「사용자 코멘트」 절에 있다.\n이 셋을 함께 보고 대중 공략 전략 초안을 작성한다.\n\n[전제 — 바꾸지 않는다]\n이 요청은 경쟁이 적은 카테고리로 판정되어 배정됐다.\n따라서 approach 는 반드시 \"대중 공략\" 이다. 내용을 보고 다시 판단하지 않는다.\n\n[대중 공략 전략이란 무엇인가]\n경쟁자가 적은 시장에서 진짜 상대는 다른 브랜드가 아니라 \"없어도 그만\"이라는 무관심이다.\n- 우선 이 물건이 왜 필요한지부터 납득시킨다. 브랜드 비교는 그다음이다.\n- 고객층을 좁히지 않는다. 카테고리를 쓰는 사람 자체를 늘리는 그림을 그린다.\n- 표현을 쉽게 쓴다. 이 카테고리를 처음 접하는 사람이 읽는다고 생각한다.\n- 먼저 자리 잡는 것이 목표다. 시장이 커졌을 때 기준으로 떠오를 이름이 되게 한다.\n\n[각 항목 작성 지침]\n- approach: \"대중 공략\" 으로 고정한다.\n\n- positioning: 한 문장. \"이 카테고리 하면 떠오르는 브랜드\"가 되는 자리를 잡는다.\n  좁은 틈새가 아니라 카테고리를 대표하는 자리로 쓴다.\n\n- rationale_md: 왜 이 자리인지 3~5문장. 마크다운 허용.\n  반드시 두 가지를 근거로 든다.\n    (1) market_brief 에서 — 왜 지금 이 카테고리가 알려질 시점인지 (트렌드를 근거로)\n    (2) 제품 분석에서 — 우리 강점 중 무엇이 카테고리를 대표할 만한지\n  제품 분석의 약점 중 처음 사는 사람이 가장 걸릴 것 하나를 골라\n  어떻게 덜어 줄지 한 문장으로 적는다.\n\n- core_strategies: 3~5개. label 은 5~15자 명사구, detail 은 실행 단위로 한 문장.\n  적어도 하나는 \"이 제품이 왜 필요한지 알리는\" 전략이어야 한다.\n  \"브랜드 인지도를 높인다\" 처럼 누구에게나 해당하는 문장은 쓰지 않는다.\n\n[하지 않을 것]\n- 숫자를 지어내지 않는다. 받은 입력에 없는 수치는 쓰지 않는다.\n- 없는 경쟁사를 만들어 내지 않는다. 경쟁이 적다는 것이 이 경로의 전제다.\n- 채널·예산·일정은 여기서 정하지 않는다.\n\n[참고 — core_strategies 를 쓰는 방식]\n\n이렇게 쓰지 않는다 (누구에게나 해당하는 말):\n  · label: 브랜드 인지도 제고    detail: SNS 를 활용해 인지도를 높인다.\n  · label: 시장 선점             detail: 빠르게 시장을 선점한다.\n\n이렇게 쓴다 (읽으면 그림이 그려지는 말):\n  · label: 위험을 눈으로 보여주기\n    detail: 어두운 길에서 보이는 거리를 비교해 보여, 없을 때 무슨 일이 생기는지 알린다.\n  · label: 처음 사는 사람 기준으로 고르기\n    detail: 크기·무게 선택을 견종 한 가지만 고르면 끝나게 줄인다.\n\n첫 번째가 \"왜 필요한지 알리는\" 전략이다. 이런 항목이 적어도 하나는 있어야 한다.','GEMINI_2_5_PRO','JSON','{\"approach\":\"대중 공략\",\"positioning\":\"샘플 포지셔닝\",\"rationale_md\":\"샘플 근거\",\"core_strategies\":[{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"}]}',0.013500),(15,'6507a395-7842-4ca8-a610-1e53105fd8ad','n7',4,'브랜드 전략 보고서','LLM','당신은 브랜드 컨설팅 보고서를 쓰는 사람이다. 전략 초안을 받아 최종 보고서 형식으로 정리한다.\n\n[입력 위치]\n아래 「입력」 절에 전략 초안이 있다 (approach · positioning · rationale_md · core_strategies).\n브랜드명이 든 기획 요청서는 「사용자 코멘트」 절에 있다.\n참고 자료의 보고서 작성 가이드를 형식 기준으로 삼는다.\n\n[이 노드가 하는 일]\n새 전략을 만드는 게 아니라 받은 전략을 보고서 형식에 맞게 다듬는 일이다.\n초안에 없는 전략을 새로 추가하지 않는다. 초안의 방향을 뒤집지 않는다.\n\n[개수 규칙 — 가장 중요하다]\npurpose · positioning · reasons · core_strategy 는 각각 정확히 3개다.\n2개도 4개도 안 된다. 서식의 칸이 3개씩이라 개수가 맞지 않으면 보고서가 깨진다.\n\n초안의 core_strategies 가 4개나 5개로 오는 경우가 있다. 그때는 옮겨 적는 것이 아니라\n3개를 고르는 것이다. 고르는 기준은 이 순서다.\n  1) 초안의 positioning 을 가장 직접적으로 실현하는 것\n  2) 읽고 바로 무엇을 할지 그림이 그려지는 것\n  3) 나머지 둘과 겹치지 않는 것\n3개보다 적게 왔다면, 초안의 rationale_md 에서 근거를 찾아 같은 방향으로 채운다.\n\n[발행 정보]\n발행일이나 작성 기관을 지어내지 않는다. 출력에 넣지 않는다. 실행 시스템이 나중에 채운다.\n\n[각 항목 작성 지침]\n- brand_name: 요청서의 브랜드명을 그대로 쓴다. 바꾸거나 다듬지 않는다.\n\n- headline: 표지에 들어갈 한 문장. 20자 내외.\n  브랜드가 무엇이 되려는지를 담는다. 회사 슬로건처럼 짧고 단정하게 쓴다.\n  느낌표를 쓰지 않는다. 보고서 표지다.\n\n- purpose(브랜드 목적) 3개: 이 브랜드가 왜 존재하는가.\n  label 만 있는 한 줄 항목이다. 10~20자 명사구로 쓴다.\n\n- positioning(브랜드 포지셔닝) 3개: 초안의 positioning 한 문장을 세 갈래로 펼친다.\n  누구에게 / 무엇으로 / 어떻게 기억될지. 각 10~20자 명사구.\n\n- reasons(선택의 이유) 3개: 고객이 이 브랜드를 고를 이유.\n  axis 는 관점의 이름이다 — 서로 다른 세 축을 쓴다 (예: 기능 / 메시지 / 제품).\n  detail 은 그 축에서 왜 우리를 고르는지 한 문장.\n\n- core_strategy(핵심 전략) 3개: 초안의 core_strategies 에서 고른다. 위 개수 규칙 참고.\n  label 만 있는 한 줄 항목이므로, 초안의 label 을 살리되 길면 줄인다.\n\n- messages(메시지 체계): 세 문장이 한 벌이다. 같은 말을 세 번 쓰지 않는다.\n  · core  — 광고에 한 줄만 쓴다면 이것. 20자 내외\n  · sub   — core 를 뒷받침하는 설명. 30자 내외\n  · daily — 고객이 일상에서 쓸 법한 말투. 20자 내외\n\n- summary_md: 보고서를 덮고 나서 남는 한 문단. 3~5문장. 마크다운 허용.\n  전략을 요약하되, 무엇을 하지 않기로 했는지도 한 문장 넣는다.\n\n[하지 않을 것]\n- 숫자를 지어내지 않는다. 초안에 없는 수치는 쓰지 않는다.\n- 채널·예산·일정을 정하지 않는다.\n- 초안의 approach 가 \"차별화\" 인지 \"대중 공략\" 인지에 따라 글의 결이 달라야 한다.\n  차별화면 좁혀서 이기는 이야기, 대중 공략이면 넓혀서 알리는 이야기다.\n\n[참고 — messages 세 문장의 관계]\n\n같은 말을 세 번 쓰면 안 된다:\n  core  : 매일 쓰는 칫솔, 매일 좋은 칫솔\n  sub   : 매일 쓰기 좋은 칫솔입니다\n  daily : 매일 좋은 칫솔 쓰세요\n\n세 문장이 각자 할 일이 있다:\n  core  : 바꿀 때를 놓치지 않는 칫솔\n  sub   : 색이 빠지면 교체 신호, 3개월마다 알아서 도착합니다\n  daily : 어, 색 빠졌네. 갈 때 됐구나\n\n[참고 — reasons 의 axis 는 서로 달라야 한다]\n\n이렇게 쓰지 않는다 (세 축이 사실상 같음):\n  기능 / 성능 / 품질\n\n이렇게 쓴다:\n  기능 중심 — 무엇을 해 주는가\n  메시지 중심 — 어떤 태도를 보여 주는가\n  제품 중심 — 손에 쥐었을 때 무엇이 다른가','GEMINI_2_5_PRO','JSON','{\"brand_name\":\"샘플 브랜드명\",\"headline\":\"샘플 대제목\",\"purpose\":[{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"}],\"positioning\":[{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"}],\"reasons\":[{\"axis\":\"샘플 축\",\"detail\":\"샘플 설명\"},{\"axis\":\"샘플 축\",\"detail\":\"샘플 설명\"},{\"axis\":\"샘플 축\",\"detail\":\"샘플 설명\"}],\"core_strategy\":[{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"}],\"messages\":{\"core\":\"샘플 핵심 메시지\",\"sub\":\"샘플 보조 메시지\",\"daily\":\"샘플 일상 메시지\"},\"summary_md\":null}',0.013500),(16,'1c5913bb-9336-4460-9bca-64eab747e886','n2',4,'캠페인 기획','LLM','당신은 SNS 캠페인 기획자다. 다른 담당자가 작성한 브랜드 전략 보고서를 받아 캠페인 계획을 세운다.\n\n[입력 위치]\n브랜드 전략 보고서는 아래 「사용자 코멘트」 절에 있다. 앞 단계 결과가 「입력」 절로 함께\n올 때는 그쪽을 보고서로 삼는다.\n\n[출발점]\n받은 보고서가 이 캠페인의 근거다. 보고서에 없는 방향을 새로 만들지 않는다.\n특히 messages(핵심·보조·일상 메시지)와 positioning 은 캠페인 전체가 따라야 할 축이다.\n\n[각 항목 작성 지침]\n- objective: 한 문장. 이 캠페인이 끝났을 때 무엇이 달라져 있어야 하는지 쓴다.\n  \"인지도를 높인다\" 처럼 어느 브랜드에나 해당하는 문장은 쓰지 않는다.\n  보고서의 핵심 전략 중 하나와 이어져야 한다.\n\n- channels: 1~4개. 많이 고르지 않는다. 실제로 운영할 수 있는 수로 좁힌다.\n  name 은 채널 이름, reason 은 왜 이 브랜드에 이 채널인지.\n  reason 에는 보고서의 내용(포지셔닝·메시지·고객)이 근거로 들어가야 한다.\n  \"타깃이 많이 사용한다\" 처럼 브랜드와 무관한 이유만 쓰면 안 된다.\n\n- posts: 3~6개. 시간 순서대로 쓴다.\n  phase 는 시기(\"1주차\", \"출시 직후\" 등), theme 는 그 게시물이 말할 것,\n  format 은 형식(릴스 · 카드뉴스 · 사진 1장 등).\n  앞쪽 게시물과 뒤쪽 게시물이 같은 말을 하면 안 된다. 순서에 이유가 있어야 한다.\n  보고서 메시지의 세 문장(핵심 · 보조 · 일상)이 적어도 한 번씩은 쓰여야 한다.\n\n- kpis: 2~4개. name 은 무엇을 재는지, target 은 어디까지 가면 성공인지.\n  ★ 현재 수치를 모르므로 절대 수치를 만들지 않는다.\n    쓰지 않는다: \"팔로워 5,000명\", \"저장 수 300회\", \"전환율 3%\"\n    쓴다: \"캠페인 종료 시 전월 대비 증가\", \"게시물 6개 중 4개 이상에서 발생\"\n  상대 변화나 기간 내 달성 여부로 쓰면 지어내지 않고도 달성을 판정할 수 있다.\n\n[하지 않을 것]\n- 광고 문구를 쓰지 않는다. 카피는 다른 담당자가 작업한다.\n  posts[].theme 은 \"무엇을 말할 것인가\"이지 완성된 문장이 아니다.\n- 예산 금액을 쓰지 않는다.\n- 보고서에 없는 제품 기능·수치를 만들지 않는다.\n\n[참고 — channels[].reason 을 쓰는 방식]\n\n이렇게 쓰지 않는다 (어느 브랜드에나 붙는 말):\n  · name: 인스타그램   reason: 타깃 연령대가 가장 많이 사용하는 채널이다.\n\n이렇게 쓴다 (보고서가 근거로 들어감):\n  · name: 인스타그램\n    reason: 교체 시점을 색 변화로 보여 주는 것이 핵심 전략이라, 전후 비교가\n            한 장으로 전달되는 이미지 중심 채널이 맞다.\n\n[참고 — kpis[].target 을 쓰는 방식]\n\n이렇게 쓰지 않는다 (지어낸 수치):\n  · name: 저장 수   target: 게시물당 300회\n\n이렇게 쓴다:\n  · name: 게시물 저장 수   target: 캠페인 종료 시 시작 4주 대비 증가\n  · name: 교체 시점 문의   target: 캠페인 기간 중 DM 문의에서 언급 발생','GEMINI_2_5_PRO','JSON','{\"objective\":\"샘플 캠페인 목표\",\"channels\":[{\"name\":\"샘플 채널\",\"reason\":\"샘플 선정 이유\"}],\"posts\":[{\"phase\":\"샘플 시기\",\"theme\":\"샘플 주제\",\"format\":\"샘플 형식\"},{\"phase\":\"샘플 시기\",\"theme\":\"샘플 주제\",\"format\":\"샘플 형식\"},{\"phase\":\"샘플 시기\",\"theme\":\"샘플 주제\",\"format\":\"샘플 형식\"}],\"kpis\":[{\"name\":\"샘플 지표\",\"target\":\"샘플 목표\"},{\"name\":\"샘플 지표\",\"target\":\"샘플 목표\"}]}',0.013500),(17,'6dfcdb77-48ce-4072-af98-c8e059a1b38a','n2',4,'광고 카피 작성','LLM','당신은 광고 카피라이터다. 다른 담당자가 작성한 브랜드 전략 보고서를 받아 광고 카피를 쓴다.\n\n[입력 위치]\n브랜드 전략 보고서는 아래 「사용자 코멘트」 절에 있다. 앞 단계 결과가 「입력」 절로 함께\n올 때는 그쪽을 보고서로 삼는다. 참고 자료의 금지 표현 목록을 함께 지킨다.\n\n[출발점]\n보고서의 메시지(핵심 · 보조 · 일상)가 카피의 원재료다.\n그 세 문장을 다듬고 펼치는 일이지, 새 컨셉을 만드는 일이 아니다.\n\n[금칙 — 하나라도 쓰면 안 된다]\n근거 자료 없이 쓸 수 없는 표현이다. 우리에게는 근거가 없다.\n  · 순위·유일성 : 최고, 최상, 1위, 유일, 단 하나, 넘버원, 최초\n  · 절대·보장   : 100%, 완벽, 절대, 보장, 반드시, 무조건, 영구\n  · 비교 우위   : 타사 대비, 경쟁사보다, 업계 평균보다\n  · 효과 단정   : 낫게 한다, 치료, 예방된다, 해결된다\n  · 근거 없는 수치 : 보고서에 없는 숫자 전부\n\n돌려 말하는 것도 금지다. \"사실상 유일한\", \"거의 100%\" 도 같은 문제다.\n\n[각 항목 작성 지침]\n- tone: 카피 전체가 유지할 말투를 한 줄로 쓴다.\n  보고서의 성격을 따른다. 안전·신뢰가 중심이면 차분하게, 일상성이 중심이면 가볍게.\n  정한 톤은 headlines 와 body_copies 전체에서 지킨다.\n\n- headlines: 3~5개. 각각 15자 내외.\n  각 항목은 {\"text\": \"문구\"} 객체다. body_copies 도 같은 객체 배열이다.\n  ★ 서로 다른 각도에서 말한다. 같은 문장의 어순만 바꾼 것은 다른 헤드라인이 아니다.\n    보고서 reasons 의 세 축(예: 기능 / 메시지 / 제품)을 서로 다른 헤드라인의\n    출발점으로 삼으면 자연스럽게 갈라진다.\n  느낌표를 최대 1개까지만 쓴다.\n\n- body_copies: 2~3개. 각각 한두 문장.\n  헤드라인이 말한 것을 뒷받침한다. 보고서 보조 메시지의 내용을 여기서 편다.\n  제품이 실제로 무엇을 해 주는지 쓴다. 감탄만 하는 문장은 쓰지 않는다.\n\n- hashtags: 3~8개. \'#\' 을 포함해 쓴다.\n  각 항목은 {\"tag\": \"#해시태그\"} 객체다.\n  구성은 이렇게 나눈다.\n    · 브랜드명 1개\n    · 카테고리·제품을 가리키는 것 1~2개\n    · 쓰는 상황이나 고객의 말 1~3개\n  같은 말의 띄어쓰기만 바꾼 태그를 늘리지 않는다.\n\n[하지 않을 것]\n- 채널이나 게시 일정을 정하지 않는다 (캠페인 기획의 몫이다).\n- 보고서에 없는 제품 기능을 만들지 않는다.\n- 브랜드명을 바꾸거나 줄여 부르지 않는다.\n\n[참고 — headlines 가 서로 달라야 한다는 것]\n\n이렇게 쓰지 않는다 (한 문장의 변주):\n  · 교체 시점을 놓치지 않는 칫솔\n  · 놓치지 않는 칫솔 교체 시점\n  · 칫솔 교체, 놓치지 마세요\n\n이렇게 쓴다 (세 축이 다름):\n  · 색이 빠지면, 바꿀 때          ← 기능 축\n  · 챙기지 않아도 챙겨집니다        ← 메시지 축\n  · 대나무 손잡이, 미세모 2배       ← 제품 축\n\n[참고 — 금칙 표현을 피해 쓰는 법]\n\n  · \"최고의 세정력\"        → \"잇몸 사이까지 닿는 미세모\"\n  · \"100% 자연 소재\"       → \"손잡이는 대나무로 만듭니다\"\n  · \"충치를 예방합니다\"     → \"닦이지 않던 곳까지 닦습니다\"\n\n효과를 단정하는 대신 제품이 하는 일을 쓰면 같은 말을 안전하게 할 수 있다.','GEMINI_2_5_PRO','JSON','{\"tone\":\"샘플 톤\",\"headlines\":[{\"text\":\"샘플 문구\"},{\"text\":\"샘플 문구\"},{\"text\":\"샘플 문구\"}],\"body_copies\":[{\"text\":\"샘플 문구\"},{\"text\":\"샘플 문구\"}],\"hashtags\":[{\"tag\":\"샘플 해시태그\"},{\"tag\":\"샘플 해시태그\"},{\"tag\":\"샘플 해시태그\"}]}',0.013500),(18,'18fdf2f8-4cd9-440a-8445-3b13e645e97c','n2',4,'제품 강점 정리','LLM','입력이 제품 정보든 완성된 소개글이든, 자기 역할을 반영한 소개글 전체를 출력하기\n원문에 있는 제품 사실과 가격을 유지하고, 없는 효능·인증·수치를 만들지 않기\n앞 단계에서 반영한 장점을 가능한 한 유지하기\n수정 이유나 평가 대신 수정된 본문만 Markdown으로 출력하기\n결과는 제목과 짧은 본문으로, 약 200~300자 이내로 제한하기\n\n제품의 특징와 구매이유가 잘 드러나도록 수정.','GEMINI_2_5_PRO','TEXT',NULL,0.013500),(19,'635c4496-3e7b-447d-886f-deeb6f49dcd5','n2',4,'고객 공감','LLM','입력이 제품 정보든 완성된 소개글이든, 자기 역할을 반영한 소개글 전체를 출력하기\n원문에 있는 제품 사실과 가격을 유지하고, 없는 효능·인증·수치를 만들지 않기\n앞 단계에서 반영한 장점을 가능한 한 유지하기\n수정 이유나 평가 대신 수정된 본문만 Markdown으로 출력하기\n결과는 제목과 짧은 본문으로, 약 200~300자 이내로 제한하기\n\n제품소개를 고객의 일상과 고민에 연결해 수정한다.','GEMINI_2_5_PRO','TEXT',NULL,0.013500),(20,'677041de-753c-4783-ab9a-71b092651884','n2',4,'브랜드 말투','LLM','입력이 제품 정보든 완성된 소개글이든, 자기 역할을 반영한 소개글 전체를 출력하기\n원문에 있는 제품 사실과 가격을 유지하고, 없는 효능·인증·수치를 만들지 않기\n앞 단계에서 반영한 장점을 가능한 한 유지하기\n수정 이유나 평가 대신 수정된 본문만 Markdown으로 출력하기\n결과는 제목과 짧은 본문으로, 약 200~300자 이내로 제한하기\n\n친근하고 기억하기 쉬운 표현으로 수정한다.','GEMINI_2_5_PRO','TEXT',NULL,0.013500),(21,'a48deb68-1f9a-4169-981d-0ca8b35fb27d','n2',2,'제품 분석','LLM','당신은 소비재 제품 기획자다. 브랜드 기획 요청서를 받아 제품 분석 결과를 작성한다.\n\n[입력 위치]\n브랜드 기획 요청서는 아래 「사용자 코멘트」 절에 있다. 그 요청서로 제품 분석 결과를 작성한다.\n앞 단계의 시장 조사 결과는 아래 「입력」 절에 있다. 그것은 market_brief 로 옮겨 적기만 하고,\nstrengths·weaknesses·opportunities 를 쓸 때는 보지 않는다.\n\n[이 분석의 범위]\n- 요청서에 적힌 제품 하나만 본다. 시장 상황·경쟁사·업계 트렌드는 다루지 않는다.\n- \"경쟁사 대비\", \"업계 평균보다\" 같은 비교 표현을 쓰지 않는다.\n- 숫자를 지어내지 않는다. 요청서에 있는 수치만 쓴다.\n\n[각 항목 작성 지침]\n- market_brief: 「입력」 절 시장 조사 결과에서 경쟁 강도 판정(competition_level)과 그 근거,\n  경쟁사 이름, 주요 트렌드를 3~4문장으로 옮겨 적는다. 해석하거나 덧붙이지 않는다.\n\n- strengths: 제품이 실제로 잘하는 것 2~5개.\n  label 은 5~15자 명사구, detail 은 한 문장.\n  요청서의 \'차별점\'을 그대로 옮겨 적지 말고, 그것이 고객에게 무엇을 해 주는지로 바꿔 쓴다.\n\n- weaknesses: 2~5개. 고객이 구매를 망설일 이유를 쓴다.\n  우리 사정(원가·생산 난이도)이 아니라 고객이 느낄 불편·불안·부담으로 쓴다.\n  좋게 들리게 다듬지 않는다. \"장점의 이면\" 형태로 쓰면 약점이 아니다.\n  예를 들어 가격이 높다면 \"왜 비싼지 설명이 필요하다\"가 아니라\n  \"같은 쓰임의 더 싼 제품이 있는데 굳이 이걸 살 이유를 모른다\"로 쓴다.\n\n- opportunities: 2~5개. 약점을 덮거나 강점을 키울 여지.\n  label 만 있는 한 줄짜리 항목이다. 짧은 명사구로 쓴다.\n\n[요청서의 \'추가 요청\']\n하지 말라고 적힌 것은 여기서도 지킨다. 표현 방식에 대한 요청이면 문장에 반영한다.\n\n[참고 — weaknesses 를 쓰는 방식]\n\n이렇게 쓰지 않는다 (약점처럼 생긴 칭찬):\n  · \"친환경 소재를 써서 원가가 다소 높습니다\"\n  · \"기능이 많아 처음에는 익숙해지는 시간이 필요합니다\"\n\n이렇게 쓴다 (고객이 망설이는 지점):\n  · label: 가격 대비 체감 어려움\n    detail: 같은 값에 대용량 제품을 살 수 있는데, 차이를 써 보기 전에는 알 수 없다.\n  · label: 교체 시점을 놓침\n    detail: 언제 갈아야 하는지 눈으로 표시되지 않아 계속 쓰게 된다.','GEMINI_2_5_PRO','JSON','{\"market_brief\":\"샘플 시장 요약\",\"strengths\":[{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"}],\"weaknesses\":[{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"}],\"opportunities\":[{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"}]}',0.013500),(22,'62afbe71-c808-4db0-b83b-f87d56f281ee','n2',2,'대중 공략 전략','LLM','당신은 브랜드 전략가다. 제품 분석 결과를 받아 전략 초안을 작성한다.\n\n[입력 위치]\n아래 「입력」 절에 제품 분석 결과가 있다. 그 안의 market_brief 가 시장 조사 요약이다\n(경쟁 강도 판정 · 경쟁사 · 트렌드). 브랜드 기획 요청서는 「사용자 코멘트」 절에 있다.\n이 셋을 함께 보고 대중 공략 전략 초안을 작성한다.\n\n[전제 — 바꾸지 않는다]\n이 요청은 경쟁이 적은 카테고리로 판정되어 배정됐다.\n따라서 approach 는 반드시 \"대중 공략\" 이다. 내용을 보고 다시 판단하지 않는다.\n\n[대중 공략 전략이란 무엇인가]\n경쟁자가 적은 시장에서 진짜 상대는 다른 브랜드가 아니라 \"없어도 그만\"이라는 무관심이다.\n- 우선 이 물건이 왜 필요한지부터 납득시킨다. 브랜드 비교는 그다음이다.\n- 고객층을 좁히지 않는다. 카테고리를 쓰는 사람 자체를 늘리는 그림을 그린다.\n- 표현을 쉽게 쓴다. 이 카테고리를 처음 접하는 사람이 읽는다고 생각한다.\n- 먼저 자리 잡는 것이 목표다. 시장이 커졌을 때 기준으로 떠오를 이름이 되게 한다.\n\n[각 항목 작성 지침]\n- approach: \"대중 공략\" 으로 고정한다.\n\n- positioning: 한 문장. \"이 카테고리 하면 떠오르는 브랜드\"가 되는 자리를 잡는다.\n  좁은 틈새가 아니라 카테고리를 대표하는 자리로 쓴다.\n\n- rationale_md: 왜 이 자리인지 3~5문장. 마크다운 허용.\n  반드시 두 가지를 근거로 든다.\n    (1) market_brief 에서 — 왜 지금 이 카테고리가 알려질 시점인지 (트렌드를 근거로)\n    (2) 제품 분석에서 — 우리 강점 중 무엇이 카테고리를 대표할 만한지\n  제품 분석의 약점 중 처음 사는 사람이 가장 걸릴 것 하나를 골라\n  어떻게 덜어 줄지 한 문장으로 적는다.\n\n- core_strategies: 3~5개. label 은 5~15자 명사구, detail 은 실행 단위로 한 문장.\n  적어도 하나는 \"이 제품이 왜 필요한지 알리는\" 전략이어야 한다.\n  \"브랜드 인지도를 높인다\" 처럼 누구에게나 해당하는 문장은 쓰지 않는다.\n\n[하지 않을 것]\n- 숫자를 지어내지 않는다. 받은 입력에 없는 수치는 쓰지 않는다.\n- 없는 경쟁사를 만들어 내지 않는다. 경쟁이 적다는 것이 이 경로의 전제다.\n- 채널·예산·일정은 여기서 정하지 않는다.\n\n[참고 — core_strategies 를 쓰는 방식]\n\n이렇게 쓰지 않는다 (누구에게나 해당하는 말):\n  · label: 브랜드 인지도 제고    detail: SNS 를 활용해 인지도를 높인다.\n  · label: 시장 선점             detail: 빠르게 시장을 선점한다.\n\n이렇게 쓴다 (읽으면 그림이 그려지는 말):\n  · label: 위험을 눈으로 보여주기\n    detail: 어두운 길에서 보이는 거리를 비교해 보여, 없을 때 무슨 일이 생기는지 알린다.\n  · label: 처음 사는 사람 기준으로 고르기\n    detail: 크기·무게 선택을 견종 한 가지만 고르면 끝나게 줄인다.\n\n첫 번째가 \"왜 필요한지 알리는\" 전략이다. 이런 항목이 적어도 하나는 있어야 한다.','GEMINI_2_5_PRO','JSON','{\"approach\":\"대중 공략\",\"positioning\":\"샘플 포지셔닝\",\"rationale_md\":\"샘플 근거\",\"core_strategies\":[{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"}]}',0.013500);
/*!40000 ALTER TABLE `agent_version_node` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `agent_version_node_ref_file`
--

DROP TABLE IF EXISTS `agent_version_node_ref_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `agent_version_node_ref_file` (
  `id` varchar(36) NOT NULL,
  `version_id` varchar(36) NOT NULL,
  `node_key` varchar(64) NOT NULL,
  `ordinal` int NOT NULL,
  `file_key` varchar(255) NOT NULL,
  `filename` varchar(255) NOT NULL,
  `file_size` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_version_node_ref_file_order` (`version_id`,`node_key`,`ordinal`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `agent_version_node_ref_file`
--

LOCK TABLES `agent_version_node_ref_file` WRITE;
/*!40000 ALTER TABLE `agent_version_node_ref_file` DISABLE KEYS */;
INSERT INTO `agent_version_node_ref_file` VALUES ('5e2c566e-5bca-49e8-8f27-7969fc174a81','6dfcdb77-48ce-4072-af98-c8e059a1b38a','n2',0,'nodes/a1c119d4-0967-481c-8262-fbd89e4b80dd/ad7d7832-97bf-4b75-9560-0672d35da937.txt','광고카피_금지표현.txt',1192,'2026-09-27 19:24:12.842776'),('7b78b843-f0de-4b2f-83cf-d2f01854e59f','6507a395-7842-4ca8-a610-1e53105fd8ad','n7',0,'nodes/49e36604-0795-4217-a3a9-40e67d587736/57a8a9b3-aba1-4fbb-a9dc-f165753c4973.pdf','보고서_작성가이드.pdf',50250,'2026-09-27 18:51:17.188191'),('bb36644c-acd8-490d-8ab0-7b9d1717cb93','6507a395-7842-4ca8-a610-1e53105fd8ad','n2',0,'nodes/5b7d478c-7ffd-4fb4-9344-6d769e7eccf1/b7ee098b-3534-4948-a83e-53af1d04570f.md','시장조사_체크리스트.md',1619,'2026-09-27 18:51:17.184663');
/*!40000 ALTER TABLE `agent_version_node_ref_file` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `app_user`
--

DROP TABLE IF EXISTS `app_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `wallet_address` varchar(42) NOT NULL,
  `nickname` varchar(20) DEFAULT NULL,
  `introduction` varchar(40) DEFAULT NULL,
  `terms_agreed_at` datetime(6) DEFAULT NULL,
  `recovery_notice_confirmed_at` datetime(6) DEFAULT NULL,
  `onboarding_completed_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_app_user_wallet_address` (`wallet_address`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `app_user`
--

LOCK TABLES `app_user` WRITE;
/*!40000 ALTER TABLE `app_user` DISABLE KEYS */;
INSERT INTO `app_user` VALUES (1,'0x29893a7b0cc2b6a7ff64c3adaa93d161cc812db6','아아',NULL,'2026-09-27 08:00:46.901120','2026-09-27 08:00:46.901120','2026-09-27 08:00:46.901120',1,'2026-09-27 07:58:29.309573','2026-09-27 08:00:46.902425'),(2,'0x2e1a8f2741c16167796870dfe674ff437c188060','박판매','판매왕','2026-09-27 15:08:56.072682','2026-09-27 15:08:56.072682','2026-09-27 15:08:56.072682',3,'2026-09-27 15:08:48.025366','2026-09-28 00:07:58.266341'),(3,'0x535abafa3b650ab9fd7b2cd98c7ad06b18d37a3b','김싸피','김싸피입니다.','2026-09-27 15:15:59.127702','2026-09-27 15:15:59.127702','2026-09-27 15:15:59.127702',1,'2026-09-27 15:15:44.119093','2026-09-27 15:15:59.128210'),(4,'0xde75a3c96fae7be1132f8cddff30c10ea4204e58','김판매','김판매씨입니다.','2026-09-27 15:41:56.389874','2026-09-27 15:41:56.389874','2026-09-27 15:41:56.389874',1,'2026-09-27 15:41:08.054567','2026-09-27 15:41:56.390428'),(5,'0x3789c8697da6b4bd02bcea25e53569433cdab301',NULL,NULL,NULL,NULL,NULL,0,'2026-09-27 20:50:08.682731','2026-09-27 20:50:08.682731');
/*!40000 ALTER TABLE `app_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `charge_request`
--

DROP TABLE IF EXISTS `charge_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `charge_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_reference_id` varchar(100) NOT NULL,
  `sender_wallet_address` varchar(42) NOT NULL,
  `chain_id` bigint NOT NULL,
  `transaction_hash` varchar(66) NOT NULL,
  `requested_deposit_amount` decimal(38,6) NOT NULL,
  `credited_abc_amount` decimal(41,6) DEFAULT NULL,
  `status` varchar(30) NOT NULL,
  `status_reason` varchar(50) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `confirmed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_charge_request_chain_hash` (`chain_id`,`transaction_hash`),
  KEY `idx_charge_request_user_status` (`user_reference_id`,`status`),
  CONSTRAINT `ck_charge_request_abc_amount_positive` CHECK (((`credited_abc_amount` is null) or (`credited_abc_amount` > 0))),
  CONSTRAINT `ck_charge_request_deposit_amount_positive` CHECK ((`requested_deposit_amount` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `charge_request`
--

LOCK TABLES `charge_request` WRITE;
/*!40000 ALTER TABLE `charge_request` DISABLE KEYS */;
INSERT INTO `charge_request` VALUES (1,'4','0xde75a3c96fae7be1132f8cddff30c10ea4204e58',31221,'0x28da7f0b3b748bb104fa827a02aed974b99eda8eff8fab521688be7cac5bd77d',500.000000,NULL,'FAILED','ONCHAIN_TRANSACTION_FAILED',1,NULL,'2026-09-27 16:46:36.537828','2026-09-27 16:46:45.826285'),(2,'2','0x2e1a8f2741c16167796870dfe674ff437c188060',31221,'0x87b9bab4a13c2dd4f1fbd27d68fba27d74c94684d1f91bcec59089e70b909e12',500.000000,NULL,'FAILED','ONCHAIN_TRANSACTION_FAILED',1,NULL,'2026-09-27 17:00:42.114210','2026-09-27 17:00:46.049042'),(3,'4','0xde75a3c96fae7be1132f8cddff30c10ea4204e58',31221,'0x37a56f13985fdc9ec2b2a9f786e2e293a82a9985f11471b36e4a06735cdf1a4a',1000.000000,1000.000000,'CONFIRMED',NULL,1,'2026-09-27 18:13:56.305237','2026-09-27 18:13:53.133573','2026-09-27 18:13:56.306885'),(4,'3','0x535abafa3b650ab9fd7b2cd98c7ad06b18d37a3b',31221,'0x145b3b451b823509ada5dd01f888038ffd70f1c10dc87146e766b2fdb40e8e29',1000.000000,1000.000000,'CONFIRMED',NULL,1,'2026-09-27 19:52:07.882309','2026-09-27 19:51:57.172055','2026-09-27 19:52:07.882465'),(5,'2','0x2e1a8f2741c16167796870dfe674ff437c188060',31221,'0xcce33577006d7a312426545ab213b35778b4e58f1e563398ebd4b6dafbfd6fd3',50.000000,50.000000,'CONFIRMED',NULL,1,'2026-09-28 00:05:40.724786','2026-09-28 00:05:31.526814','2026-09-28 00:05:40.724904'),(6,'2','0x2e1a8f2741c16167796870dfe674ff437c188060',31221,'0x725a6c7f318e061d3a0dd1a0905a32cad9c2e7e7dd7acc848c5d939145167ecc',500.000000,500.000000,'CONFIRMED',NULL,1,'2026-09-28 01:44:01.861111','2026-09-28 01:43:53.269611','2026-09-28 01:44:01.861239');
/*!40000 ALTER TABLE `charge_request` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `execution`
--

DROP TABLE IF EXISTS `execution`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `execution` (
  `id` varchar(36) NOT NULL,
  `workflow_id` varchar(36) NOT NULL,
  `requester_id` bigint NOT NULL,
  `run_id` varchar(64) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `prompt_count` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `input_file_key` varchar(128) DEFAULT NULL,
  `input_file_name` varchar(255) DEFAULT NULL,
  `input_file_size` bigint DEFAULT NULL,
  `request_id` varchar(36) NOT NULL,
  `reserved_abc` decimal(41,6) DEFAULT NULL,
  `mock` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_execution_requester_request` (`requester_id`,`request_id`),
  KEY `idx_execution_requester_created` (`requester_id`,`created_at`,`id`),
  KEY `idx_execution_workflow` (`workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `execution`
--

LOCK TABLES `execution` WRITE;
/*!40000 ALTER TABLE `execution` DISABLE KEYS */;
INSERT INTO `execution` VALUES ('00710eed-0213-4d78-8c27-8d52e098ed0b','47485ac2-f022-45bc-a604-60b3064dc8f4',3,'20260927-195447-495','SUCCEEDED',3,'2026-09-27 19:54:47.468172','2026-09-27 19:55:31.595033',NULL,NULL,NULL,'22569888-8b53-4955-aeb4-8eca8224806f',0.040500,0),('2676d6e8-c735-4214-81c3-ea345545ef34','becef94d-a89b-4001-8353-3568bccc4a1f',2,'20260928-012955-899','SUCCEEDED',2,'2026-09-28 01:29:55.877265','2026-09-28 01:30:37.984957',NULL,NULL,NULL,'e3e9486f-bf8b-4bcb-a9fb-851359814a8a',0.027000,0),('280e79c0-9c28-4884-aba4-a827eb4645dc','3486d1bb-79ff-40c0-a9bc-9dd8a906cef9',4,'20260927-194542-594','SUCCEEDED',1,'2026-09-27 19:45:42.569857','2026-09-27 19:45:58.644965',NULL,NULL,NULL,'2ba82ac8-8eb1-47ec-a73d-634465eb9932',0.013500,0),('2d158630-a3ea-4a58-bceb-45291cef65cd','4d04999f-0741-426d-8ffb-0cdb14b51f2f',4,'20260928-014704-941','SUCCEEDED',3,'2026-09-28 01:47:04.916125','2026-09-28 01:47:59.074575',NULL,NULL,NULL,'f74a45e8-f16e-495b-9d3b-12f2c96495a6',0.040500,0),('3954b23b-e4e1-46f4-9014-d2c785f4f43c','ff4893e6-25bf-477a-ba8b-c7770ada3b1b',4,'20260927-184319-806','SUCCEEDED',5,'2026-09-27 18:43:19.765434','2026-09-27 18:44:52.005786',NULL,NULL,NULL,'b798655f-43f1-4947-ab1d-6974516dbff3',0.067500,0),('3b5bdd76-480a-4f97-af51-1f4ddec13ece','becef94d-a89b-4001-8353-3568bccc4a1f',2,'20260928-013132-383','SUCCEEDED',7,'2026-09-28 01:31:32.352162','2026-09-28 01:33:38.614130',NULL,NULL,NULL,'cf4616f2-b937-47f9-8e32-df82004e075c',0.094500,0),('40d20393-e3de-4de4-9781-114dd9e29eea','becef94d-a89b-4001-8353-3568bccc4a1f',2,'20260928-012822-373','SUCCEEDED',3,'2026-09-28 01:28:22.349813','2026-09-28 01:29:14.476998',NULL,NULL,NULL,'266fdc74-4307-4e82-81a1-8c5befa18837',0.040500,0),('43765fd3-1ad0-4374-9000-09604f1557c2','ff4893e6-25bf-477a-ba8b-c7770ada3b1b',4,'20260927-181627-394','SUCCEEDED',5,'2026-09-27 18:16:27.313203','2026-09-27 18:18:03.716185',NULL,NULL,NULL,'7cf18bbd-8eac-4ec7-92c0-2b17c6599897',0.067500,0),('4acf8685-bd67-4947-88a9-56221ac5040a','4aa70c6b-7878-4ae1-9ae5-fc3ee3befe3b',4,'20260927-192257-286','SUCCEEDED',1,'2026-09-27 19:22:57.258636','2026-09-27 19:23:11.343681',NULL,NULL,NULL,'4d4835a4-0fe4-4aa2-ba60-a81ce35e6b6c',0.013500,0),('58416a29-8d70-42cd-ad48-941d5c4244bb','becef94d-a89b-4001-8353-3568bccc4a1f',2,'20260928-013543-366','SUCCEEDED',2,'2026-09-28 01:35:43.345262','2026-09-28 01:36:17.443454',NULL,NULL,NULL,'5f1b33d2-6c2c-4cff-9186-4187e79adc48',0.027000,0),('5ca59729-4a24-46ec-bd4c-a61eaa37e25c','ff4893e6-25bf-477a-ba8b-c7770ada3b1b',4,'20260928-005510-491','SUCCEEDED',5,'2026-09-28 00:55:10.463132','2026-09-28 00:56:34.655155',NULL,NULL,NULL,'6af1c98e-fe00-49ab-87cc-ae82086bfd1f',0.067500,0),('65962399-7be8-4176-a66d-e6d2e6bf1252','7e4a8f5e-06c0-4b63-897d-05e4b83aabb5',4,'20260927-191558-963','SUCCEEDED',1,'2026-09-27 19:15:58.937269','2026-09-27 19:16:23.035301',NULL,NULL,NULL,'8abd50f0-59f6-4415-bb05-3e3edc0ff831',0.013500,0),('6959b72c-6bd6-475c-bbdd-8ea1015f6d2a','5052cee1-9e23-4be4-97e8-f76afdac468f',4,'20260927-194748-899','SUCCEEDED',1,'2026-09-27 19:47:48.873453','2026-09-27 19:48:06.953839',NULL,NULL,NULL,'9af63c80-d476-490b-94f2-d6dd70bb8890',0.013500,0),('80f0adcb-8054-419d-a65f-c4757900ab84','becef94d-a89b-4001-8353-3568bccc4a1f',2,'20260928-013420-204','SUCCEEDED',1,'2026-09-28 01:34:20.181552','2026-09-28 01:34:46.274951',NULL,NULL,NULL,'aa92446b-74a4-4fec-97a1-99af0725a2e2',0.013500,0),('84df239e-fdd4-4242-8d8f-3156ec8bc004','ff4893e6-25bf-477a-ba8b-c7770ada3b1b',4,'20260928-003906-446','SUCCEEDED',5,'2026-09-28 00:39:06.418047','2026-09-28 00:40:48.646027',NULL,NULL,NULL,'9c077cc6-bcc9-4f4c-a91d-4e805d800d68',0.067500,0),('90f8de56-d35e-4667-962d-747d79902857','becef94d-a89b-4001-8353-3568bccc4a1f',2,'20260928-012753-851','FAILED',3,'2026-09-28 01:27:53.825067','2026-09-28 01:27:55.874212','executions/90f8de56-d35e-4667-962d-747d79902857/input.csv','제품 설명.csv',279,'daa0fc03-70c6-4764-a5c6-e9abb6c187f9',0.040500,0),('aaf7d29a-79bb-41b9-b273-11627b69aa0e','47485ac2-f022-45bc-a604-60b3064dc8f4',3,'20260928-001043-501','SUCCEEDED',3,'2026-09-28 00:10:43.475236','2026-09-28 00:11:27.599572',NULL,NULL,NULL,'cd4597a7-d47d-4c0b-9fa3-373cc6e42e4b',0.040500,0),('c09cf817-62c9-42e1-8ac2-7100f0d23a1d','d2afd46d-0c93-47ea-9d30-17172bd0d913',2,'20260928-003505-143','SUCCEEDED',1,'2026-09-28 00:35:05.120652','2026-09-28 00:35:25.206015',NULL,NULL,NULL,'b892da9c-4eef-4bd0-b86e-bda94ce55869',0.013500,0),('cca21b88-9e5e-44ea-8972-1fdc2eba6d5e','30078b76-e5cf-4709-a37d-f0ca95e5264e',4,'20260927-193845-292','SUCCEEDED',1,'2026-09-27 19:38:45.262941','2026-09-27 19:39:01.349421',NULL,NULL,NULL,'793dc6b2-4737-424a-9528-dfd49f2b303a',0.013500,0),('e8a8f17f-edb4-4f8f-ab54-e227ae9e5364','ff4893e6-25bf-477a-ba8b-c7770ada3b1b',4,'20260927-182244-252','SUCCEEDED',5,'2026-09-27 18:22:44.212065','2026-09-27 18:24:26.487045',NULL,NULL,NULL,'761f8bfb-7f6f-4478-b845-3ecbd627d07c',0.067500,0),('eefac4f2-aa27-41a9-bf14-b6dc1fe6dd53','8795d8c3-c1fc-4814-9c99-9f07ed2bd3d7',2,'20260928-002404-839','SUCCEEDED',1,'2026-09-28 00:24:04.816416','2026-09-28 00:24:26.902947',NULL,NULL,NULL,'76f36770-8f07-4675-9538-0fb8b9265d3a',0.013500,0),('f8be5b1f-fdc4-4b58-b12e-d071220cfc7f','ff4893e6-25bf-477a-ba8b-c7770ada3b1b',4,'20260928-014532-501','SUCCEEDED',5,'2026-09-28 01:45:32.474303','2026-09-28 01:47:16.685114',NULL,NULL,NULL,'9e426baa-d37f-4e8c-b9dd-16beb7cad4a7',0.067500,0);
/*!40000 ALTER TABLE `execution` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `execution_charge_rate`
--

DROP TABLE IF EXISTS `execution_charge_rate`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `execution_charge_rate` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `execution_id` varchar(36) NOT NULL,
  `step_id` varchar(16) NOT NULL,
  `price_multiplier` decimal(6,4) NOT NULL,
  `unit_price` decimal(41,6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_execution_charge_rate_step` (`execution_id`,`step_id`)
) ENGINE=InnoDB AUTO_INCREMENT=65 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `execution_charge_rate`
--

LOCK TABLES `execution_charge_rate` WRITE;
/*!40000 ALTER TABLE `execution_charge_rate` DISABLE KEYS */;
INSERT INTO `execution_charge_rate` VALUES (1,'43765fd3-1ad0-4374-9000-09604f1557c2','n4',1.0000,0.013500),(2,'43765fd3-1ad0-4374-9000-09604f1557c2','n3',1.0000,0.013500),(3,'43765fd3-1ad0-4374-9000-09604f1557c2','n2',1.0000,0.013500),(4,'43765fd3-1ad0-4374-9000-09604f1557c2','n1',1.0000,0.013500),(5,'43765fd3-1ad0-4374-9000-09604f1557c2','n5',1.0000,0.013500),(6,'e8a8f17f-edb4-4f8f-ab54-e227ae9e5364','n4',1.0000,0.013500),(7,'e8a8f17f-edb4-4f8f-ab54-e227ae9e5364','n3',1.0000,0.013500),(8,'e8a8f17f-edb4-4f8f-ab54-e227ae9e5364','n2',1.0000,0.013500),(9,'e8a8f17f-edb4-4f8f-ab54-e227ae9e5364','n1',1.0000,0.013500),(10,'e8a8f17f-edb4-4f8f-ab54-e227ae9e5364','n5',1.0000,0.013500),(11,'3954b23b-e4e1-46f4-9014-d2c785f4f43c','n4',1.0000,0.013500),(12,'3954b23b-e4e1-46f4-9014-d2c785f4f43c','n3',1.0000,0.013500),(13,'3954b23b-e4e1-46f4-9014-d2c785f4f43c','n2',1.0000,0.013500),(14,'3954b23b-e4e1-46f4-9014-d2c785f4f43c','n1',1.0000,0.013500),(15,'3954b23b-e4e1-46f4-9014-d2c785f4f43c','n5',1.0000,0.013500),(16,'65962399-7be8-4176-a66d-e6d2e6bf1252','n1',1.0000,0.013500),(17,'4acf8685-bd67-4947-88a9-56221ac5040a','n1',1.0000,0.013500),(18,'cca21b88-9e5e-44ea-8972-1fdc2eba6d5e','n1',1.0000,0.013500),(19,'280e79c0-9c28-4884-aba4-a827eb4645dc','n1',1.0000,0.013500),(20,'6959b72c-6bd6-475c-bbdd-8ea1015f6d2a','n1',1.0000,0.013500),(21,'00710eed-0213-4d78-8c27-8d52e098ed0b','a3.n1',1.0000,0.013500),(22,'00710eed-0213-4d78-8c27-8d52e098ed0b','a2.n1',1.0000,0.013500),(23,'00710eed-0213-4d78-8c27-8d52e098ed0b','a1.n1',1.0000,0.013500),(24,'aaf7d29a-79bb-41b9-b273-11627b69aa0e','a3.n1',1.0000,0.013500),(25,'aaf7d29a-79bb-41b9-b273-11627b69aa0e','a2.n1',1.0000,0.013500),(26,'aaf7d29a-79bb-41b9-b273-11627b69aa0e','a1.n1',1.0000,0.013500),(27,'eefac4f2-aa27-41a9-bf14-b6dc1fe6dd53','n1',1.0000,0.013500),(28,'c09cf817-62c9-42e1-8ac2-7100f0d23a1d','n1',1.0000,0.013500),(29,'84df239e-fdd4-4242-8d8f-3156ec8bc004','a2.n1',1.0000,0.013500),(30,'84df239e-fdd4-4242-8d8f-3156ec8bc004','a1.n1',1.0000,0.013500),(31,'84df239e-fdd4-4242-8d8f-3156ec8bc004','n2',1.0000,0.013500),(32,'84df239e-fdd4-4242-8d8f-3156ec8bc004','n1',1.0000,0.013500),(33,'84df239e-fdd4-4242-8d8f-3156ec8bc004','n3',1.0000,0.013500),(34,'5ca59729-4a24-46ec-bd4c-a61eaa37e25c','a2.n1',1.0000,0.013500),(35,'5ca59729-4a24-46ec-bd4c-a61eaa37e25c','a1.n1',1.0000,0.013500),(36,'5ca59729-4a24-46ec-bd4c-a61eaa37e25c','n2',1.0000,0.013500),(37,'5ca59729-4a24-46ec-bd4c-a61eaa37e25c','n1',1.0000,0.013500),(38,'5ca59729-4a24-46ec-bd4c-a61eaa37e25c','n3',1.0000,0.013500),(39,'90f8de56-d35e-4667-962d-747d79902857','a3.n1',1.0000,0.013500),(40,'90f8de56-d35e-4667-962d-747d79902857','a2.n1',1.0000,0.013500),(41,'90f8de56-d35e-4667-962d-747d79902857','a1.n1',1.0000,0.013500),(42,'40d20393-e3de-4de4-9781-114dd9e29eea','a3.n1',1.0000,0.013500),(43,'40d20393-e3de-4de4-9781-114dd9e29eea','a2.n1',1.0000,0.013500),(44,'40d20393-e3de-4de4-9781-114dd9e29eea','a1.n1',1.0000,0.013500),(45,'2676d6e8-c735-4214-81c3-ea345545ef34','a2.n1',1.0000,0.013500),(46,'2676d6e8-c735-4214-81c3-ea345545ef34','a1.n1',1.0000,0.013500),(47,'3b5bdd76-480a-4f97-af51-1f4ddec13ece','a2.n1',1.0000,0.013500),(48,'3b5bdd76-480a-4f97-af51-1f4ddec13ece','a3.n1',1.0000,0.013500),(49,'3b5bdd76-480a-4f97-af51-1f4ddec13ece','a1.n5',1.0000,0.013500),(50,'3b5bdd76-480a-4f97-af51-1f4ddec13ece','a1.n4',1.0000,0.013500),(51,'3b5bdd76-480a-4f97-af51-1f4ddec13ece','a1.n3',1.0000,0.013500),(52,'3b5bdd76-480a-4f97-af51-1f4ddec13ece','a1.n2',1.0000,0.013500),(53,'3b5bdd76-480a-4f97-af51-1f4ddec13ece','a1.n1',1.0000,0.013500),(54,'80f0adcb-8054-419d-a65f-c4757900ab84','a1.n1',1.0000,0.013500),(55,'58416a29-8d70-42cd-ad48-941d5c4244bb','a2.n1',1.0000,0.013500),(56,'58416a29-8d70-42cd-ad48-941d5c4244bb','a1.n1',1.0000,0.013500),(57,'f8be5b1f-fdc4-4b58-b12e-d071220cfc7f','a2.n1',1.0000,0.013500),(58,'f8be5b1f-fdc4-4b58-b12e-d071220cfc7f','a1.n1',1.0000,0.013500),(59,'f8be5b1f-fdc4-4b58-b12e-d071220cfc7f','n2',1.0000,0.013500),(60,'f8be5b1f-fdc4-4b58-b12e-d071220cfc7f','n1',1.0000,0.013500),(61,'f8be5b1f-fdc4-4b58-b12e-d071220cfc7f','n3',1.0000,0.013500),(62,'2d158630-a3ea-4a58-bceb-45291cef65cd','a3.n1',1.0000,0.013500),(63,'2d158630-a3ea-4a58-bceb-45291cef65cd','a2.n1',1.0000,0.013500),(64,'2d158630-a3ea-4a58-bceb-45291cef65cd','a1.n1',1.0000,0.013500);
/*!40000 ALTER TABLE `execution_charge_rate` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `execution_failure`
--

DROP TABLE IF EXISTS `execution_failure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `execution_failure` (
  `execution_id` varchar(36) NOT NULL,
  `failed_step_id` varchar(16) DEFAULT NULL,
  `failure_code` varchar(64) DEFAULT NULL,
  `fault` varchar(20) NOT NULL,
  `affects_success_rate` tinyint(1) NOT NULL,
  `supplier_id` bigint DEFAULT NULL,
  `agent_id` varchar(36) DEFAULT NULL,
  PRIMARY KEY (`execution_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `execution_failure`
--

LOCK TABLES `execution_failure` WRITE;
/*!40000 ALTER TABLE `execution_failure` DISABLE KEYS */;
INSERT INTO `execution_failure` VALUES ('90f8de56-d35e-4667-962d-747d79902857','a1.n1','FILE_PARSE_FAILED','CONSUMER',0,NULL,NULL);
/*!40000 ALTER TABLE `execution_failure` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `execution_result`
--

DROP TABLE IF EXISTS `execution_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `execution_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `execution_id` varchar(36) NOT NULL,
  `format` varchar(20) NOT NULL,
  `file_key` varchar(128) NOT NULL,
  `file_size` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_execution_result_format` (`execution_id`,`format`)
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `execution_result`
--

LOCK TABLES `execution_result` WRITE;
/*!40000 ALTER TABLE `execution_result` DISABLE KEYS */;
INSERT INTO `execution_result` VALUES (1,'43765fd3-1ad0-4374-9000-09604f1557c2','JSON','executions/43765fd3-1ad0-4374-9000-09604f1557c2/result.json',2063),(2,'43765fd3-1ad0-4374-9000-09604f1557c2','PDF','executions/43765fd3-1ad0-4374-9000-09604f1557c2/result.pdf',59551),(3,'e8a8f17f-edb4-4f8f-ab54-e227ae9e5364','JSON','executions/e8a8f17f-edb4-4f8f-ab54-e227ae9e5364/result.json',2071),(4,'e8a8f17f-edb4-4f8f-ab54-e227ae9e5364','PDF','executions/e8a8f17f-edb4-4f8f-ab54-e227ae9e5364/result.pdf',62229),(5,'3954b23b-e4e1-46f4-9014-d2c785f4f43c','JSON','executions/3954b23b-e4e1-46f4-9014-d2c785f4f43c/result.json',2075),(6,'3954b23b-e4e1-46f4-9014-d2c785f4f43c','PDF','executions/3954b23b-e4e1-46f4-9014-d2c785f4f43c/result.pdf',62126),(7,'65962399-7be8-4176-a66d-e6d2e6bf1252','JSON','executions/65962399-7be8-4176-a66d-e6d2e6bf1252/result.json',2321),(8,'65962399-7be8-4176-a66d-e6d2e6bf1252','PDF','executions/65962399-7be8-4176-a66d-e6d2e6bf1252/result.pdf',68127),(9,'4acf8685-bd67-4947-88a9-56221ac5040a','JSON','executions/4acf8685-bd67-4947-88a9-56221ac5040a/result.json',1029),(10,'4acf8685-bd67-4947-88a9-56221ac5040a','PDF','executions/4acf8685-bd67-4947-88a9-56221ac5040a/result.pdf',43233),(11,'cca21b88-9e5e-44ea-8972-1fdc2eba6d5e','JSON','executions/cca21b88-9e5e-44ea-8972-1fdc2eba6d5e/result.json',784),(12,'cca21b88-9e5e-44ea-8972-1fdc2eba6d5e','PDF','executions/cca21b88-9e5e-44ea-8972-1fdc2eba6d5e/result.pdf',41584),(13,'280e79c0-9c28-4884-aba4-a827eb4645dc','JSON','executions/280e79c0-9c28-4884-aba4-a827eb4645dc/result.json',893),(14,'280e79c0-9c28-4884-aba4-a827eb4645dc','PDF','executions/280e79c0-9c28-4884-aba4-a827eb4645dc/result.pdf',41137),(15,'6959b72c-6bd6-475c-bbdd-8ea1015f6d2a','JSON','executions/6959b72c-6bd6-475c-bbdd-8ea1015f6d2a/result.json',753),(16,'6959b72c-6bd6-475c-bbdd-8ea1015f6d2a','PDF','executions/6959b72c-6bd6-475c-bbdd-8ea1015f6d2a/result.pdf',40280),(17,'00710eed-0213-4d78-8c27-8d52e098ed0b','JSON','executions/00710eed-0213-4d78-8c27-8d52e098ed0b/result.json',639),(18,'00710eed-0213-4d78-8c27-8d52e098ed0b','PDF','executions/00710eed-0213-4d78-8c27-8d52e098ed0b/result.pdf',35666),(19,'aaf7d29a-79bb-41b9-b273-11627b69aa0e','JSON','executions/aaf7d29a-79bb-41b9-b273-11627b69aa0e/result.json',641),(20,'aaf7d29a-79bb-41b9-b273-11627b69aa0e','PDF','executions/aaf7d29a-79bb-41b9-b273-11627b69aa0e/result.pdf',33344),(21,'eefac4f2-aa27-41a9-bf14-b6dc1fe6dd53','JSON','executions/eefac4f2-aa27-41a9-bf14-b6dc1fe6dd53/result.json',2173),(22,'eefac4f2-aa27-41a9-bf14-b6dc1fe6dd53','PDF','executions/eefac4f2-aa27-41a9-bf14-b6dc1fe6dd53/result.pdf',72610),(23,'c09cf817-62c9-42e1-8ac2-7100f0d23a1d','JSON','executions/c09cf817-62c9-42e1-8ac2-7100f0d23a1d/result.json',1835),(24,'c09cf817-62c9-42e1-8ac2-7100f0d23a1d','PDF','executions/c09cf817-62c9-42e1-8ac2-7100f0d23a1d/result.pdf',66244),(25,'84df239e-fdd4-4242-8d8f-3156ec8bc004','JSON','executions/84df239e-fdd4-4242-8d8f-3156ec8bc004/result.json',2338),(26,'84df239e-fdd4-4242-8d8f-3156ec8bc004','PDF','executions/84df239e-fdd4-4242-8d8f-3156ec8bc004/result.pdf',68108),(27,'5ca59729-4a24-46ec-bd4c-a61eaa37e25c','JSON','executions/5ca59729-4a24-46ec-bd4c-a61eaa37e25c/result.json',2184),(28,'5ca59729-4a24-46ec-bd4c-a61eaa37e25c','PDF','executions/5ca59729-4a24-46ec-bd4c-a61eaa37e25c/result.pdf',59531),(29,'40d20393-e3de-4de4-9781-114dd9e29eea','JSON','executions/40d20393-e3de-4de4-9781-114dd9e29eea/result.json',861),(30,'40d20393-e3de-4de4-9781-114dd9e29eea','PDF','executions/40d20393-e3de-4de4-9781-114dd9e29eea/result.pdf',41157),(31,'2676d6e8-c735-4214-81c3-ea345545ef34','JSON','executions/2676d6e8-c735-4214-81c3-ea345545ef34/result.json',767),(32,'2676d6e8-c735-4214-81c3-ea345545ef34','PDF','executions/2676d6e8-c735-4214-81c3-ea345545ef34/result.pdf',39097),(33,'3b5bdd76-480a-4f97-af51-1f4ddec13ece','JSON','executions/3b5bdd76-480a-4f97-af51-1f4ddec13ece/result.json',1765),(34,'3b5bdd76-480a-4f97-af51-1f4ddec13ece','PDF','executions/3b5bdd76-480a-4f97-af51-1f4ddec13ece/result.pdf',53878),(35,'80f0adcb-8054-419d-a65f-c4757900ab84','JSON','executions/80f0adcb-8054-419d-a65f-c4757900ab84/result.json',2389),(36,'80f0adcb-8054-419d-a65f-c4757900ab84','PDF','executions/80f0adcb-8054-419d-a65f-c4757900ab84/result.pdf',69938),(37,'58416a29-8d70-42cd-ad48-941d5c4244bb','JSON','executions/58416a29-8d70-42cd-ad48-941d5c4244bb/result.json',834),(38,'58416a29-8d70-42cd-ad48-941d5c4244bb','PDF','executions/58416a29-8d70-42cd-ad48-941d5c4244bb/result.pdf',42282),(39,'f8be5b1f-fdc4-4b58-b12e-d071220cfc7f','JSON','executions/f8be5b1f-fdc4-4b58-b12e-d071220cfc7f/result.json',2179),(40,'f8be5b1f-fdc4-4b58-b12e-d071220cfc7f','PDF','executions/f8be5b1f-fdc4-4b58-b12e-d071220cfc7f/result.pdf',58085),(41,'2d158630-a3ea-4a58-bceb-45291cef65cd','JSON','executions/2d158630-a3ea-4a58-bceb-45291cef65cd/result.json',846),(42,'2d158630-a3ea-4a58-bceb-45291cef65cd','PDF','executions/2d158630-a3ea-4a58-bceb-45291cef65cd/result.pdf',41929);
/*!40000 ALTER TABLE `execution_result` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `execution_settlement`
--

DROP TABLE IF EXISTS `execution_settlement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `execution_settlement` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `execution_id` varchar(36) NOT NULL,
  `step_id` varchar(16) NOT NULL,
  `node_name` varchar(40) DEFAULT NULL,
  `supplier_id` bigint NOT NULL,
  `agent_id` varchar(36) DEFAULT NULL,
  `amount_abc` decimal(41,6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_execution_settlement_step` (`execution_id`,`step_id`),
  KEY `idx_execution_settlement_supplier_created` (`supplier_id`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=55 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `execution_settlement`
--

LOCK TABLES `execution_settlement` WRITE;
/*!40000 ALTER TABLE `execution_settlement` DISABLE KEYS */;
INSERT INTO `execution_settlement` VALUES (1,'43765fd3-1ad0-4374-9000-09604f1557c2','n1','시장 조사',4,NULL,0.013500,'2026-09-27 18:18:03.736243'),(2,'43765fd3-1ad0-4374-9000-09604f1557c2','n2','제품 분석',4,NULL,0.013500,'2026-09-27 18:18:03.741304'),(3,'43765fd3-1ad0-4374-9000-09604f1557c2','n3','차별화 전략',4,NULL,0.013500,'2026-09-27 18:18:03.743123'),(4,'43765fd3-1ad0-4374-9000-09604f1557c2','n5','브랜드 전략 보고서',4,NULL,0.013500,'2026-09-27 18:18:03.744715'),(5,'e8a8f17f-edb4-4f8f-ab54-e227ae9e5364','n1','시장 조사',4,NULL,0.013500,'2026-09-27 18:24:26.501064'),(6,'e8a8f17f-edb4-4f8f-ab54-e227ae9e5364','n2','제품 분석',4,NULL,0.013500,'2026-09-27 18:24:26.502401'),(7,'e8a8f17f-edb4-4f8f-ab54-e227ae9e5364','n3','차별화 전략',4,NULL,0.013500,'2026-09-27 18:24:26.503762'),(8,'e8a8f17f-edb4-4f8f-ab54-e227ae9e5364','n5','브랜드 전략 보고서',4,NULL,0.013500,'2026-09-27 18:24:26.504975'),(9,'3954b23b-e4e1-46f4-9014-d2c785f4f43c','n1','시장 조사',4,NULL,0.013500,'2026-09-27 18:44:52.019834'),(10,'3954b23b-e4e1-46f4-9014-d2c785f4f43c','n2','제품 분석',4,NULL,0.013500,'2026-09-27 18:44:52.021222'),(11,'3954b23b-e4e1-46f4-9014-d2c785f4f43c','n4','대중 공략 전략',4,NULL,0.013500,'2026-09-27 18:44:52.022186'),(12,'3954b23b-e4e1-46f4-9014-d2c785f4f43c','n5','브랜드 전략 보고서',4,NULL,0.013500,'2026-09-27 18:44:52.023188'),(13,'65962399-7be8-4176-a66d-e6d2e6bf1252','n1','캠페인 기획',4,NULL,0.013500,'2026-09-27 19:16:23.031301'),(14,'4acf8685-bd67-4947-88a9-56221ac5040a','n1','광고 카피 작성',4,NULL,0.013500,'2026-09-27 19:23:11.337577'),(15,'cca21b88-9e5e-44ea-8972-1fdc2eba6d5e','n1','제품 강점 정리',4,NULL,0.013500,'2026-09-27 19:39:01.345457'),(16,'280e79c0-9c28-4884-aba4-a827eb4645dc','n1','고객 공감',4,NULL,0.013500,'2026-09-27 19:45:58.641479'),(17,'6959b72c-6bd6-475c-bbdd-8ea1015f6d2a','n1','브랜드 말투',4,NULL,0.013500,'2026-09-27 19:48:06.949814'),(18,'00710eed-0213-4d78-8c27-8d52e098ed0b','a1.n1','고객 공감',4,'313ddd93-f8cd-4f78-a4e7-b23685c37fa4',0.013500,'2026-09-27 19:55:31.603358'),(19,'00710eed-0213-4d78-8c27-8d52e098ed0b','a2.n1','제품 강점 정리',4,'689bed13-64b2-4988-8cfe-d59beac85ce5',0.013500,'2026-09-27 19:55:31.604725'),(20,'00710eed-0213-4d78-8c27-8d52e098ed0b','a3.n1','브랜드 말투',4,'788477e9-33c2-4bae-9b10-7edab198048f',0.013500,'2026-09-27 19:55:31.605656'),(21,'aaf7d29a-79bb-41b9-b273-11627b69aa0e','a1.n1','고객 공감',4,'313ddd93-f8cd-4f78-a4e7-b23685c37fa4',0.013500,'2026-09-28 00:11:27.605310'),(22,'aaf7d29a-79bb-41b9-b273-11627b69aa0e','a2.n1','제품 강점 정리',4,'689bed13-64b2-4988-8cfe-d59beac85ce5',0.013500,'2026-09-28 00:11:27.606513'),(23,'aaf7d29a-79bb-41b9-b273-11627b69aa0e','a3.n1','브랜드 말투',4,'788477e9-33c2-4bae-9b10-7edab198048f',0.013500,'2026-09-28 00:11:27.607540'),(24,'eefac4f2-aa27-41a9-bf14-b6dc1fe6dd53','n1','제품 분석',2,NULL,0.013500,'2026-09-28 00:24:26.898754'),(25,'c09cf817-62c9-42e1-8ac2-7100f0d23a1d','n1','대중 공략 전략',2,NULL,0.013500,'2026-09-28 00:35:25.200597'),(26,'84df239e-fdd4-4242-8d8f-3156ec8bc004','n1','시장 조사',4,NULL,0.013500,'2026-09-28 00:40:48.660637'),(27,'84df239e-fdd4-4242-8d8f-3156ec8bc004','a1.n1','제품 분석',2,'d26b84a4-558e-4694-b8f8-ae629d4d0a23',0.013500,'2026-09-28 00:40:48.661837'),(28,'84df239e-fdd4-4242-8d8f-3156ec8bc004','n2','차별화 전략',4,NULL,0.013500,'2026-09-28 00:40:48.662774'),(29,'84df239e-fdd4-4242-8d8f-3156ec8bc004','n3','브랜드 전략 보고서',4,NULL,0.013500,'2026-09-28 00:40:48.663484'),(30,'5ca59729-4a24-46ec-bd4c-a61eaa37e25c','n1','시장 조사',4,NULL,0.013500,'2026-09-28 00:56:34.666678'),(31,'5ca59729-4a24-46ec-bd4c-a61eaa37e25c','a1.n1','제품 분석',2,'d26b84a4-558e-4694-b8f8-ae629d4d0a23',0.013500,'2026-09-28 00:56:34.667525'),(32,'5ca59729-4a24-46ec-bd4c-a61eaa37e25c','n2','차별화 전략',4,NULL,0.013500,'2026-09-28 00:56:34.668841'),(33,'5ca59729-4a24-46ec-bd4c-a61eaa37e25c','n3','브랜드 전략 보고서',4,NULL,0.013500,'2026-09-28 00:56:34.669710'),(34,'40d20393-e3de-4de4-9781-114dd9e29eea','a1.n1','고객 공감',4,'313ddd93-f8cd-4f78-a4e7-b23685c37fa4',0.013500,'2026-09-28 01:29:14.481749'),(35,'40d20393-e3de-4de4-9781-114dd9e29eea','a2.n1','제품 강점 정리',4,'689bed13-64b2-4988-8cfe-d59beac85ce5',0.013500,'2026-09-28 01:29:14.482673'),(36,'40d20393-e3de-4de4-9781-114dd9e29eea','a3.n1','브랜드 말투',4,'788477e9-33c2-4bae-9b10-7edab198048f',0.013500,'2026-09-28 01:29:14.483406'),(37,'2676d6e8-c735-4214-81c3-ea345545ef34','a1.n1','브랜드 말투',4,'788477e9-33c2-4bae-9b10-7edab198048f',0.013500,'2026-09-28 01:30:37.989655'),(38,'2676d6e8-c735-4214-81c3-ea345545ef34','a2.n1','제품 강점 정리',4,'689bed13-64b2-4988-8cfe-d59beac85ce5',0.013500,'2026-09-28 01:30:37.990718'),(39,'3b5bdd76-480a-4f97-af51-1f4ddec13ece','a1.n1','시장 조사',4,'ee9a8750-f0ce-4a2a-b46d-db40a71862f3',0.013500,'2026-09-28 01:33:38.627259'),(40,'3b5bdd76-480a-4f97-af51-1f4ddec13ece','a1.n2','제품 분석',4,'ee9a8750-f0ce-4a2a-b46d-db40a71862f3',0.013500,'2026-09-28 01:33:38.628032'),(41,'3b5bdd76-480a-4f97-af51-1f4ddec13ece','a1.n3','차별화 전략',4,'ee9a8750-f0ce-4a2a-b46d-db40a71862f3',0.013500,'2026-09-28 01:33:38.628652'),(42,'3b5bdd76-480a-4f97-af51-1f4ddec13ece','a1.n5','브랜드 전략 보고서',4,'ee9a8750-f0ce-4a2a-b46d-db40a71862f3',0.013500,'2026-09-28 01:33:38.629267'),(43,'3b5bdd76-480a-4f97-af51-1f4ddec13ece','a2.n1','고객 공감',4,'313ddd93-f8cd-4f78-a4e7-b23685c37fa4',0.013500,'2026-09-28 01:33:38.629938'),(44,'3b5bdd76-480a-4f97-af51-1f4ddec13ece','a3.n1','캠페인 기획',4,'225ee29a-527b-4557-8d55-3e2748cdd7b0',0.013500,'2026-09-28 01:33:38.630534'),(45,'80f0adcb-8054-419d-a65f-c4757900ab84','a1.n1','캠페인 기획',4,'225ee29a-527b-4557-8d55-3e2748cdd7b0',0.013500,'2026-09-28 01:34:46.277658'),(46,'58416a29-8d70-42cd-ad48-941d5c4244bb','a1.n1','고객 공감',4,'313ddd93-f8cd-4f78-a4e7-b23685c37fa4',0.013500,'2026-09-28 01:36:17.447314'),(47,'58416a29-8d70-42cd-ad48-941d5c4244bb','a2.n1','브랜드 말투',4,'788477e9-33c2-4bae-9b10-7edab198048f',0.013500,'2026-09-28 01:36:17.448433'),(48,'f8be5b1f-fdc4-4b58-b12e-d071220cfc7f','n1','시장 조사',4,NULL,0.013500,'2026-09-28 01:47:16.697583'),(49,'f8be5b1f-fdc4-4b58-b12e-d071220cfc7f','a1.n1','제품 분석',2,'d26b84a4-558e-4694-b8f8-ae629d4d0a23',0.013500,'2026-09-28 01:47:16.698811'),(50,'f8be5b1f-fdc4-4b58-b12e-d071220cfc7f','n2','차별화 전략',4,NULL,0.013500,'2026-09-28 01:47:16.699676'),(51,'f8be5b1f-fdc4-4b58-b12e-d071220cfc7f','n3','브랜드 전략 보고서',4,NULL,0.013500,'2026-09-28 01:47:16.700393'),(52,'2d158630-a3ea-4a58-bceb-45291cef65cd','a1.n1','광고 카피 작성',4,'385b3023-2306-4680-8ad6-f3991b4bb90c',0.013500,'2026-09-28 01:47:59.050619'),(53,'2d158630-a3ea-4a58-bceb-45291cef65cd','a2.n1','고객 공감',4,'313ddd93-f8cd-4f78-a4e7-b23685c37fa4',0.013500,'2026-09-28 01:47:59.051664'),(54,'2d158630-a3ea-4a58-bceb-45291cef65cd','a3.n1','브랜드 말투',4,'788477e9-33c2-4bae-9b10-7edab198048f',0.013500,'2026-09-28 01:47:59.052524');
/*!40000 ALTER TABLE `execution_settlement` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flyway_schema_history`
--

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'1','create account','SQL','V1__create_account.sql',921144339,'admin','2026-09-26 20:28:36',153,1),(2,'2','create spring session','SQL','V2__create_spring_session.sql',772262976,'admin','2026-09-26 20:28:36',142,1),(3,'3','create payment','SQL','V3__create_payment.sql',372035780,'admin','2026-09-26 20:28:36',344,1),(4,'4','create node','SQL','V4__create_node.sql',212094924,'admin','2026-09-26 20:28:37',166,1),(5,'5','create workflow','SQL','V5__create_workflow.sql',-368370924,'admin','2026-09-26 20:28:37',214,1),(6,'6','create agent','SQL','V6__create_agent.sql',-1557743690,'admin','2026-09-26 20:28:37',540,1),(7,'7','create execution','SQL','V7__create_execution.sql',1017598929,'admin','2026-09-26 20:28:38',270,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `graph_edge`
--

DROP TABLE IF EXISTS `graph_edge`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `graph_edge` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `graph_id` varchar(36) NOT NULL,
  `edge_key` varchar(64) NOT NULL,
  `source_node_key` varchar(64) NOT NULL,
  `target_node_key` varchar(64) NOT NULL,
  `branch_when` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_graph_edge_key` (`graph_id`,`edge_key`)
) ENGINE=InnoDB AUTO_INCREMENT=74 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `graph_edge`
--

LOCK TABLES `graph_edge` WRITE;
/*!40000 ALTER TABLE `graph_edge` DISABLE KEYS */;
INSERT INTO `graph_edge` VALUES (1,'ff41fe27-9be4-45f9-9ecb-8d0907ff57ae','e1','n1','n2',NULL),(2,'ff41fe27-9be4-45f9-9ecb-8d0907ff57ae','e2','n2','n3',NULL),(3,'ff41fe27-9be4-45f9-9ecb-8d0907ff57ae','e3','n3','n4',NULL),(4,'ff41fe27-9be4-45f9-9ecb-8d0907ff57ae','e4','n4','n5',1),(5,'ff41fe27-9be4-45f9-9ecb-8d0907ff57ae','e5','n4','n6',0),(6,'ff41fe27-9be4-45f9-9ecb-8d0907ff57ae','e6','n5','n7',NULL),(7,'ff41fe27-9be4-45f9-9ecb-8d0907ff57ae','e7','n6','n7',NULL),(8,'ff41fe27-9be4-45f9-9ecb-8d0907ff57ae','e8','n7','n8',NULL),(12,'5c036ec2-efb3-4d21-b7e5-9828730e544a','e1','n1','n2',NULL),(13,'5c036ec2-efb3-4d21-b7e5-9828730e544a','e2','n2','n3',NULL),(16,'c45be2ea-0095-4483-b2a3-897b01fe21e6','e1','n1','n2',NULL),(17,'c45be2ea-0095-4483-b2a3-897b01fe21e6','e2','n2','n3',NULL),(18,'c45be2ea-0095-4483-b2a3-897b01fe21e6','e3','n3','n4',NULL),(19,'c45be2ea-0095-4483-b2a3-897b01fe21e6','e4','n4','n5',1),(20,'c45be2ea-0095-4483-b2a3-897b01fe21e6','e5','n4','n6',0),(21,'c45be2ea-0095-4483-b2a3-897b01fe21e6','e6','n5','n7',NULL),(22,'c45be2ea-0095-4483-b2a3-897b01fe21e6','e7','n6','n7',NULL),(23,'c45be2ea-0095-4483-b2a3-897b01fe21e6','e8','n7','n8',NULL),(24,'2f26713b-a934-42cd-93dc-973a15084021','e1','n1','n2',NULL),(25,'2f26713b-a934-42cd-93dc-973a15084021','e2','n2','n3',NULL),(26,'5d9915d1-c4fc-4c55-bc7a-c57f0718be55','e1','n1','n2',NULL),(27,'5d9915d1-c4fc-4c55-bc7a-c57f0718be55','e2','n2','n3',NULL),(28,'c225171c-a9a5-4f1c-bba6-2cee3b4c3289','e1','n1','n2',NULL),(29,'c225171c-a9a5-4f1c-bba6-2cee3b4c3289','e2','n2','n3',NULL),(30,'ce3074e0-7fdb-472e-84dc-a07f985d0be6','e1','n1','n2',NULL),(31,'ce3074e0-7fdb-472e-84dc-a07f985d0be6','e2','n2','n3',NULL),(32,'5b801053-d621-488b-b40c-55b8f1c06621','e1','n1','n2',NULL),(33,'5b801053-d621-488b-b40c-55b8f1c06621','e2','n2','n3',NULL),(34,'c76d6191-0e3f-4ccd-a6a1-e63b0f5984cb','e1','n1','n2',NULL),(35,'c76d6191-0e3f-4ccd-a6a1-e63b0f5984cb','e2','n2','n3',NULL),(36,'7250fad1-35c6-4ce6-aa61-680fc629532a','e1','n1','n2',NULL),(37,'7250fad1-35c6-4ce6-aa61-680fc629532a','e2','n2','n3',NULL),(38,'748fcbd2-f9c6-40e7-a925-6d9351d23543','e1','n1','n2',NULL),(39,'748fcbd2-f9c6-40e7-a925-6d9351d23543','e2','n2','n3',NULL),(40,'13562329-fdcd-4628-b3fa-74659c5aeee7','e1','n1','n2',NULL),(41,'13562329-fdcd-4628-b3fa-74659c5aeee7','e2','n2','n3',NULL),(42,'8f2e7beb-952d-4891-847a-9616b6dfd74a','e1','n1','n2',NULL),(43,'8f2e7beb-952d-4891-847a-9616b6dfd74a','e2','n2','n3',NULL),(44,'25084168-82b2-4efd-8bcd-97e567c666f1','e1','n1','n2',NULL),(45,'25084168-82b2-4efd-8bcd-97e567c666f1','e2','n2','n3',NULL),(46,'25084168-82b2-4efd-8bcd-97e567c666f1','e3','n3','n4',NULL),(47,'25084168-82b2-4efd-8bcd-97e567c666f1','e4','n4','n5',NULL),(48,'a26da5a2-5a12-452e-9398-4d40baf4aec4','e1','n1','n2',NULL),(49,'a26da5a2-5a12-452e-9398-4d40baf4aec4','e2','n2','n3',NULL),(50,'a26da5a2-5a12-452e-9398-4d40baf4aec4','e3','n3','n4',NULL),(51,'a26da5a2-5a12-452e-9398-4d40baf4aec4','e4','n4','n5',NULL),(52,'e4e343b4-f151-4f2d-8a1c-0aa019cb6a10','e1','n1','n2',NULL),(53,'e4e343b4-f151-4f2d-8a1c-0aa019cb6a10','e2','n2','n3',NULL),(54,'3701e8c4-7b3a-466d-98d3-458498c64b56','e1','n1','n2',NULL),(55,'3701e8c4-7b3a-466d-98d3-458498c64b56','e2','n2','n3',NULL),(56,'a0adf15c-a862-40b7-977c-bcdca51b2dc6','e1','n1','n2',NULL),(57,'a0adf15c-a862-40b7-977c-bcdca51b2dc6','e2','n2','n3',NULL),(58,'1507d9fb-99f8-41ba-9bf5-af02c350f276','e1','n1','n2',NULL),(59,'1507d9fb-99f8-41ba-9bf5-af02c350f276','e2','n2','n3',NULL),(60,'9930a62c-f22a-434b-a7ee-67e2d16f9b19','e1','n1','n2',NULL),(61,'9930a62c-f22a-434b-a7ee-67e2d16f9b19','e2','n2','n3',NULL),(62,'9930a62c-f22a-434b-a7ee-67e2d16f9b19','e3','n3','n4',NULL),(63,'9930a62c-f22a-434b-a7ee-67e2d16f9b19','e4','n4','n5',NULL),(64,'00b499b9-25e8-481a-8fd3-68bca08b0f44','e1','n1','n2',NULL),(65,'00b499b9-25e8-481a-8fd3-68bca08b0f44','e2','n2','n3',NULL),(69,'00b499b9-25e8-481a-8fd3-68bca08b0f44','e3','n3','n4',NULL),(70,'d36290ed-d794-4a0d-8023-6d57b9823e99','e1','n1','n2',NULL),(71,'d36290ed-d794-4a0d-8023-6d57b9823e99','e2','n2','n3',NULL),(72,'d36290ed-d794-4a0d-8023-6d57b9823e99','e3','n3','n4',NULL),(73,'d36290ed-d794-4a0d-8023-6d57b9823e99','e4','n4','n5',NULL);
/*!40000 ALTER TABLE `graph_edge` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `graph_node`
--

DROP TABLE IF EXISTS `graph_node`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `graph_node` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `graph_id` varchar(36) NOT NULL,
  `node_key` varchar(64) NOT NULL,
  `source_type` varchar(10) NOT NULL,
  `reference_id` varchar(36) DEFAULT NULL,
  `version_id` varchar(36) DEFAULT NULL,
  `input_source` varchar(20) NOT NULL,
  `control_type` varchar(20) DEFAULT NULL,
  `branch_condition` text,
  `io_type` varchar(20) DEFAULT NULL,
  `io_fields` text,
  `result_shape` varchar(20) DEFAULT NULL,
  `result_format` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_graph_node_key` (`graph_id`,`node_key`),
  KEY `idx_graph_node_reference` (`reference_id`)
) ENGINE=InnoDB AUTO_INCREMENT=116 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `graph_node`
--

LOCK TABLES `graph_node` WRITE;
/*!40000 ALTER TABLE `graph_node` DISABLE KEYS */;
INSERT INTO `graph_node` VALUES (1,'f3b571be-e276-4436-a10e-ea7b4676e6d2','n1','NODE','b0e4a224-b904-41ae-b3d7-53d184f493c3',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(3,'f5bbcdaa-ab09-4ac3-92f3-1b30df9b80ec','n1','NODE','5d35100b-3481-4811-b30d-0f160e6fef7b',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(5,'7fcf88e6-1b94-4c25-9c80-79f29d03ca19','n1','NODE','2949bc67-f9c5-4378-8c8a-0d13c9be9655',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(7,'c1e33fa7-4475-4cb5-876d-0042395d0685','n1','NODE','b7e95b28-b316-42b5-8955-14f77181d0a1',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(9,'252cbd11-ade7-44e2-823f-40851b1f25ba','n1','NODE','f0fed48f-146a-4263-9f67-c46539e864cb',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(11,'9d38f62b-a4c5-48fc-a053-1e830005b66c','n1','NODE','7a0d075b-d413-425d-b4c4-4305f1165e36',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(13,'74bd5257-a9e4-4d30-850c-42fac85e5640','n1','NODE','185f9dcf-3fa8-48ba-ba2b-7936865bf40a',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(15,'0cefc92d-98ec-47fa-a0d9-550d031c43b1','n1','NODE','9c43ac5a-b17c-48ed-a0ed-635568731d0e',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(17,'4ee6ab67-3b68-4c89-b8ce-582ab5ccd097','n1','NODE','39a44ae2-78ba-448f-aa20-160ab6a61657',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(19,'a06a1444-56a3-4f88-8864-8145a2794bfe','n1','NODE','7b56b8ff-f0c9-4a75-b86b-d9984b01759d',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(21,'ff41fe27-9be4-45f9-9ecb-8d0907ff57ae','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(22,'ff41fe27-9be4-45f9-9ecb-8d0907ff57ae','n2','NODE','5b7d478c-7ffd-4fb4-9344-6d769e7eccf1',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(23,'ff41fe27-9be4-45f9-9ecb-8d0907ff57ae','n3','AGENT','d26b84a4-558e-4694-b8f8-ae629d4d0a23','a48deb68-1f9a-4169-981d-0ca8b35fb27d','PREV',NULL,NULL,NULL,NULL,NULL,NULL),(24,'ff41fe27-9be4-45f9-9ecb-8d0907ff57ae','n4','CONTROL',NULL,NULL,'PREV','BRANCH','{\"left\":\"n2.competition_level\",\"operator\":\"EQ\",\"right\":\"high\"}',NULL,NULL,NULL,NULL),(25,'ff41fe27-9be4-45f9-9ecb-8d0907ff57ae','n5','NODE','39902c01-ff78-482e-a22d-4abc1d87288b',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(26,'ff41fe27-9be4-45f9-9ecb-8d0907ff57ae','n6','AGENT','fd6db8f4-9d26-4be1-be5b-c94e854afbae','62afbe71-c808-4db0-b83b-f87d56f281ee','PREV',NULL,NULL,NULL,NULL,NULL,NULL),(27,'ff41fe27-9be4-45f9-9ecb-8d0907ff57ae','n7','NODE','49e36604-0795-4217-a3a9-40e67d587736',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(28,'ff41fe27-9be4-45f9-9ecb-8d0907ff57ae','n8','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(33,'5c036ec2-efb3-4d21-b7e5-9828730e544a','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(34,'5c036ec2-efb3-4d21-b7e5-9828730e544a','n2','NODE','012dde84-cf50-4518-94cc-9f8e2799d3d6',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(35,'5c036ec2-efb3-4d21-b7e5-9828730e544a','n3','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(39,'c45be2ea-0095-4483-b2a3-897b01fe21e6','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(40,'c45be2ea-0095-4483-b2a3-897b01fe21e6','n2','NODE','5b7d478c-7ffd-4fb4-9344-6d769e7eccf1',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(41,'c45be2ea-0095-4483-b2a3-897b01fe21e6','n3','NODE','f71d5ea8-f55b-4b0b-9bc7-01153cb8410c',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(42,'c45be2ea-0095-4483-b2a3-897b01fe21e6','n4','CONTROL',NULL,NULL,'PREV','BRANCH','{\"left\":\"n2.competition_level\",\"operator\":\"EQ\",\"right\":\"high\"}',NULL,NULL,NULL,NULL),(43,'c45be2ea-0095-4483-b2a3-897b01fe21e6','n5','NODE','39902c01-ff78-482e-a22d-4abc1d87288b',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(44,'c45be2ea-0095-4483-b2a3-897b01fe21e6','n6','NODE','1eeb3a20-0f09-4134-b95f-fd58f540bed6',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(45,'c45be2ea-0095-4483-b2a3-897b01fe21e6','n7','NODE','49e36604-0795-4217-a3a9-40e67d587736',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(46,'c45be2ea-0095-4483-b2a3-897b01fe21e6','n8','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(47,'2f26713b-a934-42cd-93dc-973a15084021','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(48,'2f26713b-a934-42cd-93dc-973a15084021','n2','NODE','6a1ef052-6f07-458c-9c67-71aa5c3f2267',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(49,'2f26713b-a934-42cd-93dc-973a15084021','n3','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(50,'5d9915d1-c4fc-4c55-bc7a-c57f0718be55','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(51,'5d9915d1-c4fc-4c55-bc7a-c57f0718be55','n2','NODE','6a1ef052-6f07-458c-9c67-71aa5c3f2267',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(52,'5d9915d1-c4fc-4c55-bc7a-c57f0718be55','n3','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(53,'c225171c-a9a5-4f1c-bba6-2cee3b4c3289','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(54,'c225171c-a9a5-4f1c-bba6-2cee3b4c3289','n2','NODE','a1c119d4-0967-481c-8262-fbd89e4b80dd',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(55,'c225171c-a9a5-4f1c-bba6-2cee3b4c3289','n3','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(56,'ce3074e0-7fdb-472e-84dc-a07f985d0be6','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(57,'ce3074e0-7fdb-472e-84dc-a07f985d0be6','n2','NODE','a1c119d4-0967-481c-8262-fbd89e4b80dd',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(58,'ce3074e0-7fdb-472e-84dc-a07f985d0be6','n3','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(59,'5b801053-d621-488b-b40c-55b8f1c06621','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(60,'5b801053-d621-488b-b40c-55b8f1c06621','n2','NODE','c0dd0a2b-9b4c-4377-9625-1d43d865f235',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(61,'5b801053-d621-488b-b40c-55b8f1c06621','n3','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(62,'c76d6191-0e3f-4ccd-a6a1-e63b0f5984cb','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(63,'c76d6191-0e3f-4ccd-a6a1-e63b0f5984cb','n2','NODE','c0dd0a2b-9b4c-4377-9625-1d43d865f235',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(64,'c76d6191-0e3f-4ccd-a6a1-e63b0f5984cb','n3','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(65,'7250fad1-35c6-4ce6-aa61-680fc629532a','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(66,'7250fad1-35c6-4ce6-aa61-680fc629532a','n2','NODE','9f3ac6b2-063b-4232-9b3d-8093b4867d5d',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(67,'7250fad1-35c6-4ce6-aa61-680fc629532a','n3','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(68,'748fcbd2-f9c6-40e7-a925-6d9351d23543','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(69,'748fcbd2-f9c6-40e7-a925-6d9351d23543','n2','NODE','9f3ac6b2-063b-4232-9b3d-8093b4867d5d',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(70,'748fcbd2-f9c6-40e7-a925-6d9351d23543','n3','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(71,'13562329-fdcd-4628-b3fa-74659c5aeee7','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(72,'13562329-fdcd-4628-b3fa-74659c5aeee7','n2','NODE','cdb67c15-1945-4a73-8706-4d2171fa9cbc',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(73,'13562329-fdcd-4628-b3fa-74659c5aeee7','n3','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(74,'8f2e7beb-952d-4891-847a-9616b6dfd74a','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(75,'8f2e7beb-952d-4891-847a-9616b6dfd74a','n2','NODE','cdb67c15-1945-4a73-8706-4d2171fa9cbc',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(76,'8f2e7beb-952d-4891-847a-9616b6dfd74a','n3','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(77,'25084168-82b2-4efd-8bcd-97e567c666f1','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(78,'25084168-82b2-4efd-8bcd-97e567c666f1','n2','AGENT','313ddd93-f8cd-4f78-a4e7-b23685c37fa4','635c4496-3e7b-447d-886f-deeb6f49dcd5','PREV',NULL,NULL,NULL,NULL,NULL,NULL),(79,'25084168-82b2-4efd-8bcd-97e567c666f1','n3','AGENT','689bed13-64b2-4988-8cfe-d59beac85ce5','18fdf2f8-4cd9-440a-8445-3b13e645e97c','PREV',NULL,NULL,NULL,NULL,NULL,NULL),(80,'25084168-82b2-4efd-8bcd-97e567c666f1','n4','AGENT','788477e9-33c2-4bae-9b10-7edab198048f','677041de-753c-4783-ab9a-71b092651884','PREV',NULL,NULL,NULL,NULL,NULL,NULL),(81,'25084168-82b2-4efd-8bcd-97e567c666f1','n5','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(82,'a26da5a2-5a12-452e-9398-4d40baf4aec4','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(83,'a26da5a2-5a12-452e-9398-4d40baf4aec4','n2','AGENT','788477e9-33c2-4bae-9b10-7edab198048f','677041de-753c-4783-ab9a-71b092651884','PREV',NULL,NULL,NULL,NULL,NULL,NULL),(84,'a26da5a2-5a12-452e-9398-4d40baf4aec4','n3','AGENT','689bed13-64b2-4988-8cfe-d59beac85ce5','18fdf2f8-4cd9-440a-8445-3b13e645e97c','PREV',NULL,NULL,NULL,NULL,NULL,NULL),(85,'a26da5a2-5a12-452e-9398-4d40baf4aec4','n4','AGENT','313ddd93-f8cd-4f78-a4e7-b23685c37fa4','635c4496-3e7b-447d-886f-deeb6f49dcd5','PREV',NULL,NULL,NULL,NULL,NULL,NULL),(86,'a26da5a2-5a12-452e-9398-4d40baf4aec4','n5','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(87,'e4e343b4-f151-4f2d-8a1c-0aa019cb6a10','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(88,'e4e343b4-f151-4f2d-8a1c-0aa019cb6a10','n2','NODE','30e1e43d-762b-46a3-b1ad-6836ccf8ad58',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(89,'e4e343b4-f151-4f2d-8a1c-0aa019cb6a10','n3','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(90,'3701e8c4-7b3a-466d-98d3-458498c64b56','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(91,'3701e8c4-7b3a-466d-98d3-458498c64b56','n2','NODE','30e1e43d-762b-46a3-b1ad-6836ccf8ad58',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(92,'3701e8c4-7b3a-466d-98d3-458498c64b56','n3','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(93,'a0adf15c-a862-40b7-977c-bcdca51b2dc6','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(94,'a0adf15c-a862-40b7-977c-bcdca51b2dc6','n2','NODE','daa1d519-8f27-41f9-a80c-06ea8b20c9c5',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(95,'a0adf15c-a862-40b7-977c-bcdca51b2dc6','n3','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(96,'1507d9fb-99f8-41ba-9bf5-af02c350f276','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(97,'1507d9fb-99f8-41ba-9bf5-af02c350f276','n2','NODE','daa1d519-8f27-41f9-a80c-06ea8b20c9c5',NULL,'PREV',NULL,NULL,NULL,NULL,NULL,NULL),(98,'1507d9fb-99f8-41ba-9bf5-af02c350f276','n3','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(99,'9930a62c-f22a-434b-a7ee-67e2d16f9b19','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(100,'9930a62c-f22a-434b-a7ee-67e2d16f9b19','n2','AGENT','689bed13-64b2-4988-8cfe-d59beac85ce5','18fdf2f8-4cd9-440a-8445-3b13e645e97c','PREV',NULL,NULL,NULL,NULL,NULL,NULL),(101,'9930a62c-f22a-434b-a7ee-67e2d16f9b19','n3','AGENT','385b3023-2306-4680-8ad6-f3991b4bb90c','6dfcdb77-48ce-4072-af98-c8e059a1b38a','PREV',NULL,NULL,NULL,NULL,NULL,NULL),(102,'9930a62c-f22a-434b-a7ee-67e2d16f9b19','n4','AGENT','ee9a8750-f0ce-4a2a-b46d-db40a71862f3','6507a395-7842-4ca8-a610-1e53105fd8ad','PREV',NULL,NULL,NULL,NULL,NULL,NULL),(103,'9930a62c-f22a-434b-a7ee-67e2d16f9b19','n5','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(104,'00b499b9-25e8-481a-8fd3-68bca08b0f44','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(105,'00b499b9-25e8-481a-8fd3-68bca08b0f44','n2','AGENT','313ddd93-f8cd-4f78-a4e7-b23685c37fa4','635c4496-3e7b-447d-886f-deeb6f49dcd5','PREV',NULL,NULL,NULL,NULL,NULL,NULL),(106,'00b499b9-25e8-481a-8fd3-68bca08b0f44','n3','AGENT','788477e9-33c2-4bae-9b10-7edab198048f','677041de-753c-4783-ab9a-71b092651884','PREV',NULL,NULL,NULL,NULL,NULL,NULL),(110,'00b499b9-25e8-481a-8fd3-68bca08b0f44','n4','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF'),(111,'d36290ed-d794-4a0d-8023-6d57b9823e99','n1','IO',NULL,NULL,'PREV',NULL,NULL,'START',NULL,NULL,NULL),(112,'d36290ed-d794-4a0d-8023-6d57b9823e99','n2','AGENT','385b3023-2306-4680-8ad6-f3991b4bb90c','6dfcdb77-48ce-4072-af98-c8e059a1b38a','PREV',NULL,NULL,NULL,NULL,NULL,NULL),(113,'d36290ed-d794-4a0d-8023-6d57b9823e99','n3','AGENT','313ddd93-f8cd-4f78-a4e7-b23685c37fa4','635c4496-3e7b-447d-886f-deeb6f49dcd5','PREV',NULL,NULL,NULL,NULL,NULL,NULL),(114,'d36290ed-d794-4a0d-8023-6d57b9823e99','n4','AGENT','788477e9-33c2-4bae-9b10-7edab198048f','677041de-753c-4783-ab9a-71b092651884','PREV',NULL,NULL,NULL,NULL,NULL,NULL),(115,'d36290ed-d794-4a0d-8023-6d57b9823e99','n5','IO',NULL,NULL,'PREV',NULL,NULL,'END',NULL,'DOCUMENT','PDF');
/*!40000 ALTER TABLE `graph_node` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `identity_verification`
--

DROP TABLE IF EXISTS `identity_verification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `identity_verification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `legal_name` varchar(50) NOT NULL,
  `birth_date` date NOT NULL,
  `status` varchar(20) NOT NULL,
  `submitted_at` datetime(6) NOT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reject_reason` varchar(200) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_identity_verification_user` (`user_id`),
  KEY `idx_identity_verification_status` (`status`,`submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `identity_verification`
--

LOCK TABLES `identity_verification` WRITE;
/*!40000 ALTER TABLE `identity_verification` DISABLE KEYS */;
/*!40000 ALTER TABLE `identity_verification` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ledger_account`
--

DROP TABLE IF EXISTS `ledger_account`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ledger_account` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_type` varchar(30) NOT NULL,
  `reference_id` varchar(100) NOT NULL,
  `balance` decimal(41,6) NOT NULL DEFAULT '0.000000',
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ledger_account_type_reference` (`account_type`,`reference_id`)
) ENGINE=InnoDB AUTO_INCREMENT=119 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ledger_account`
--

LOCK TABLES `ledger_account` WRITE;
/*!40000 ALTER TABLE `ledger_account` DISABLE KEYS */;
INSERT INTO `ledger_account` VALUES (1,'ABC_BALANCE','1',0.000000,0,'2026-09-27 07:58:29.321310','2026-09-27 07:58:29.321310'),(2,'ABC_BALANCE','2',549.784000,12,'2026-09-27 15:08:48.033803','2026-09-28 01:44:01.866246'),(3,'ABC_BALANCE','3',999.919000,3,'2026-09-27 15:15:44.121272','2026-09-28 00:10:43.486435'),(4,'ABC_BALANCE','4',999.568000,19,'2026-09-27 15:41:08.058340','2026-09-28 01:47:16.691882'),(6,'ABC_ISSUANCE','SYSTEM',-2550.000000,4,'2026-09-27 18:13:56.383332','2026-09-28 01:44:01.868819'),(8,'EXECUTION_ESCROW','4',0.000000,30,'2026-09-27 18:16:27.352708','2026-09-28 01:47:59.077490'),(12,'PAYOUT_BALANCE','4',0.661500,19,'2026-09-27 18:18:03.759154','2026-09-28 01:47:59.079910'),(48,'EXECUTION_ESCROW','3',0.000000,4,'2026-09-27 19:54:47.480914','2026-09-28 00:11:27.612182'),(51,'ABC_BALANCE','5',0.000000,0,'2026-09-27 20:50:08.690757','2026-09-27 20:50:08.690757'),(59,'EXECUTION_ESCROW','2',0.000000,17,'2026-09-28 00:24:04.824214','2026-09-28 01:36:17.453525'),(61,'PAYOUT_BALANCE','2',0.067500,5,'2026-09-28 00:24:26.907578','2026-09-28 01:47:16.706233');
/*!40000 ALTER TABLE `ledger_account` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ledger_entry`
--

DROP TABLE IF EXISTS `ledger_entry`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ledger_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ledger_transaction_id` bigint NOT NULL,
  `ledger_account_id` bigint NOT NULL,
  `amount_delta` decimal(41,6) NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_ledger_entry_account` (`ledger_account_id`),
  KEY `idx_ledger_entry_transaction` (`ledger_transaction_id`),
  CONSTRAINT `fk_ledger_entry_account` FOREIGN KEY (`ledger_account_id`) REFERENCES `ledger_account` (`id`),
  CONSTRAINT `fk_ledger_entry_transaction` FOREIGN KEY (`ledger_transaction_id`) REFERENCES `ledger_transaction` (`id`),
  CONSTRAINT `ck_ledger_entry_amount_not_zero` CHECK ((`amount_delta` <> 0.000))
) ENGINE=InnoDB AUTO_INCREMENT=114 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ledger_entry`
--

LOCK TABLES `ledger_entry` WRITE;
/*!40000 ALTER TABLE `ledger_entry` DISABLE KEYS */;
INSERT INTO `ledger_entry` VALUES (1,1,4,1000.000000,'2026-09-27 18:13:56.390206'),(2,1,6,-1000.000000,'2026-09-27 18:13:56.394931'),(3,2,4,-0.067500,'2026-09-27 18:16:27.357930'),(4,2,8,0.067500,'2026-09-27 18:16:27.359660'),(5,3,4,0.013500,'2026-09-27 18:18:03.730993'),(6,3,8,-0.013500,'2026-09-27 18:18:03.733093'),(7,4,8,-0.054000,'2026-09-27 18:18:03.765277'),(8,4,12,0.054000,'2026-09-27 18:18:03.767099'),(9,5,4,-0.067500,'2026-09-27 18:22:44.236268'),(10,5,8,0.067500,'2026-09-27 18:22:44.237715'),(11,6,4,0.013500,'2026-09-27 18:24:26.497768'),(12,6,8,-0.013500,'2026-09-27 18:24:26.499323'),(13,7,8,-0.054000,'2026-09-27 18:24:26.522101'),(14,7,12,0.054000,'2026-09-27 18:24:26.524011'),(15,8,4,-0.067500,'2026-09-27 18:43:19.788408'),(16,8,8,0.067500,'2026-09-27 18:43:19.789828'),(17,9,4,0.013500,'2026-09-27 18:44:52.016138'),(18,9,8,-0.013500,'2026-09-27 18:44:52.018215'),(19,10,8,-0.054000,'2026-09-27 18:44:52.036248'),(20,10,12,0.054000,'2026-09-27 18:44:52.037869'),(21,11,4,-0.013500,'2026-09-27 19:15:58.952409'),(22,11,8,0.013500,'2026-09-27 19:15:58.953703'),(23,12,8,-0.013500,'2026-09-27 19:16:23.044563'),(24,12,12,0.013500,'2026-09-27 19:16:23.045872'),(25,13,4,-0.013500,'2026-09-27 19:22:57.275334'),(26,13,8,0.013500,'2026-09-27 19:22:57.276688'),(27,14,8,-0.013500,'2026-09-27 19:23:11.354229'),(28,14,12,0.013500,'2026-09-27 19:23:11.355735'),(29,15,4,-0.013500,'2026-09-27 19:38:45.280256'),(30,15,8,0.013500,'2026-09-27 19:38:45.281504'),(31,16,8,-0.013500,'2026-09-27 19:39:01.357113'),(32,16,12,0.013500,'2026-09-27 19:39:01.358451'),(33,17,4,-0.013500,'2026-09-27 19:45:42.583565'),(34,17,8,0.013500,'2026-09-27 19:45:42.584891'),(35,18,8,-0.013500,'2026-09-27 19:45:58.652763'),(36,18,12,0.013500,'2026-09-27 19:45:58.653976'),(37,19,4,-0.013500,'2026-09-27 19:47:48.885854'),(38,19,8,0.013500,'2026-09-27 19:47:48.886926'),(39,20,8,-0.013500,'2026-09-27 19:48:06.962170'),(40,20,12,0.013500,'2026-09-27 19:48:06.963383'),(41,21,3,1000.000000,'2026-09-27 19:52:07.894137'),(42,21,6,-1000.000000,'2026-09-27 19:52:07.895332'),(43,22,3,-0.040500,'2026-09-27 19:54:47.484229'),(44,22,48,0.040500,'2026-09-27 19:54:47.485473'),(45,23,48,-0.040500,'2026-09-27 19:55:31.615571'),(46,23,12,0.040500,'2026-09-27 19:55:31.616854'),(47,24,2,50.000000,'2026-09-28 00:05:40.734694'),(48,24,6,-50.000000,'2026-09-28 00:05:40.735687'),(49,25,3,-0.040500,'2026-09-28 00:10:43.490667'),(50,25,48,0.040500,'2026-09-28 00:10:43.491793'),(51,26,48,-0.040500,'2026-09-28 00:11:27.616857'),(52,26,12,0.040500,'2026-09-28 00:11:27.618116'),(53,27,2,-0.013500,'2026-09-28 00:24:04.826610'),(54,27,59,0.013500,'2026-09-28 00:24:04.827726'),(55,28,59,-0.013500,'2026-09-28 00:24:26.911681'),(56,28,61,0.013500,'2026-09-28 00:24:26.912910'),(57,29,2,-0.013500,'2026-09-28 00:35:05.133384'),(58,29,59,0.013500,'2026-09-28 00:35:05.134421'),(59,30,59,-0.013500,'2026-09-28 00:35:25.215070'),(60,30,61,0.013500,'2026-09-28 00:35:25.216215'),(61,31,4,-0.067500,'2026-09-28 00:39:06.431506'),(62,31,8,0.067500,'2026-09-28 00:39:06.432543'),(63,32,4,0.013500,'2026-09-28 00:40:48.657807'),(64,32,8,-0.013500,'2026-09-28 00:40:48.658894'),(65,33,8,-0.054000,'2026-09-28 00:40:48.674150'),(66,33,61,0.013500,'2026-09-28 00:40:48.675295'),(67,33,12,0.040500,'2026-09-28 00:40:48.676321'),(68,34,4,-0.067500,'2026-09-28 00:55:10.478997'),(69,34,8,0.067500,'2026-09-28 00:55:10.480161'),(70,35,4,0.013500,'2026-09-28 00:56:34.664636'),(71,35,8,-0.013500,'2026-09-28 00:56:34.665655'),(72,36,8,-0.054000,'2026-09-28 00:56:34.681741'),(73,36,61,0.013500,'2026-09-28 00:56:34.682823'),(74,36,12,0.040500,'2026-09-28 00:56:34.683584'),(75,37,2,-0.040500,'2026-09-28 01:27:53.839753'),(76,37,59,0.040500,'2026-09-28 01:27:53.841314'),(77,38,2,0.040500,'2026-09-28 01:27:55.880236'),(78,38,59,-0.040500,'2026-09-28 01:27:55.881164'),(79,39,2,-0.040500,'2026-09-28 01:28:22.364060'),(80,39,59,0.040500,'2026-09-28 01:28:22.365047'),(81,40,59,-0.040500,'2026-09-28 01:29:14.490078'),(82,40,12,0.040500,'2026-09-28 01:29:14.491035'),(83,41,2,-0.027000,'2026-09-28 01:29:55.889623'),(84,41,59,0.027000,'2026-09-28 01:29:55.890478'),(85,42,59,-0.027000,'2026-09-28 01:30:37.997679'),(86,42,12,0.027000,'2026-09-28 01:30:37.998515'),(87,43,2,-0.094500,'2026-09-28 01:31:32.368764'),(88,43,59,0.094500,'2026-09-28 01:31:32.369977'),(89,44,2,0.013500,'2026-09-28 01:33:38.625579'),(90,44,59,-0.013500,'2026-09-28 01:33:38.626476'),(91,45,59,-0.081000,'2026-09-28 01:33:38.637322'),(92,45,12,0.081000,'2026-09-28 01:33:38.638299'),(93,46,2,-0.013500,'2026-09-28 01:34:20.192314'),(94,46,59,0.013500,'2026-09-28 01:34:20.193247'),(95,47,59,-0.013500,'2026-09-28 01:34:46.285115'),(96,47,12,0.013500,'2026-09-28 01:34:46.286097'),(97,48,2,-0.027000,'2026-09-28 01:35:43.355896'),(98,48,59,0.027000,'2026-09-28 01:35:43.356863'),(99,49,59,-0.027000,'2026-09-28 01:36:17.457793'),(100,49,12,0.027000,'2026-09-28 01:36:17.458796'),(101,50,2,500.000000,'2026-09-28 01:44:01.870035'),(102,50,6,-500.000000,'2026-09-28 01:44:01.871007'),(103,51,4,-0.067500,'2026-09-28 01:45:32.488666'),(104,51,8,0.067500,'2026-09-28 01:45:32.489956'),(105,52,4,-0.040500,'2026-09-28 01:47:04.929419'),(106,52,8,0.040500,'2026-09-28 01:47:04.930376'),(107,53,4,0.013500,'2026-09-28 01:47:16.695602'),(108,53,8,-0.013500,'2026-09-28 01:47:16.696659'),(109,54,8,-0.054000,'2026-09-28 01:47:16.709326'),(110,54,61,0.013500,'2026-09-28 01:47:16.710238'),(111,54,12,0.040500,'2026-09-28 01:47:16.710960'),(112,55,8,-0.040500,'2026-09-28 01:47:59.081005'),(113,55,12,0.040500,'2026-09-28 01:47:59.082139');
/*!40000 ALTER TABLE `ledger_entry` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ledger_transaction`
--

DROP TABLE IF EXISTS `ledger_transaction`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ledger_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_type` varchar(30) NOT NULL,
  `idempotency_key` varchar(150) NOT NULL,
  `reference_type` varchar(30) NOT NULL,
  `reference_id` varchar(100) NOT NULL,
  `chain_id` bigint DEFAULT NULL,
  `transaction_hash` varchar(66) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ledger_transaction_idempotency` (`idempotency_key`),
  UNIQUE KEY `uq_ledger_transaction_chain_hash` (`chain_id`,`transaction_hash`)
) ENGINE=InnoDB AUTO_INCREMENT=56 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ledger_transaction`
--

LOCK TABLES `ledger_transaction` WRITE;
/*!40000 ALTER TABLE `ledger_transaction` DISABLE KEYS */;
INSERT INTO `ledger_transaction` VALUES (1,'CHARGE','charge-request:3','CHARGE_REQUEST','3',31221,'0x37a56f13985fdc9ec2b2a9f786e2e293a82a9985f11471b36e4a06735cdf1a4a','2026-09-27 18:13:56.333783'),(2,'RESERVE','execution-reserve:43765fd3-1ad0-4374-9000-09604f1557c2','EXECUTION','43765fd3-1ad0-4374-9000-09604f1557c2',NULL,NULL,'2026-09-27 18:16:27.343774'),(3,'REFUND','execution-refund:43765fd3-1ad0-4374-9000-09604f1557c2','EXECUTION','43765fd3-1ad0-4374-9000-09604f1557c2',NULL,NULL,'2026-09-27 18:18:03.713722'),(4,'SETTLEMENT','execution-settlement:43765fd3-1ad0-4374-9000-09604f1557c2','EXECUTION','43765fd3-1ad0-4374-9000-09604f1557c2',NULL,NULL,'2026-09-27 18:18:03.750518'),(5,'RESERVE','execution-reserve:e8a8f17f-edb4-4f8f-ab54-e227ae9e5364','EXECUTION','e8a8f17f-edb4-4f8f-ab54-e227ae9e5364',NULL,NULL,'2026-09-27 18:22:44.224958'),(6,'REFUND','execution-refund:e8a8f17f-edb4-4f8f-ab54-e227ae9e5364','EXECUTION','e8a8f17f-edb4-4f8f-ab54-e227ae9e5364',NULL,NULL,'2026-09-27 18:24:26.485207'),(7,'SETTLEMENT','execution-settlement:e8a8f17f-edb4-4f8f-ab54-e227ae9e5364','EXECUTION','e8a8f17f-edb4-4f8f-ab54-e227ae9e5364',NULL,NULL,'2026-09-27 18:24:26.508025'),(8,'RESERVE','execution-reserve:3954b23b-e4e1-46f4-9014-d2c785f4f43c','EXECUTION','3954b23b-e4e1-46f4-9014-d2c785f4f43c',NULL,NULL,'2026-09-27 18:43:19.778825'),(9,'REFUND','execution-refund:3954b23b-e4e1-46f4-9014-d2c785f4f43c','EXECUTION','3954b23b-e4e1-46f4-9014-d2c785f4f43c',NULL,NULL,'2026-09-27 18:44:52.003987'),(10,'SETTLEMENT','execution-settlement:3954b23b-e4e1-46f4-9014-d2c785f4f43c','EXECUTION','3954b23b-e4e1-46f4-9014-d2c785f4f43c',NULL,NULL,'2026-09-27 18:44:52.025951'),(11,'RESERVE','execution-reserve:65962399-7be8-4176-a66d-e6d2e6bf1252','EXECUTION','65962399-7be8-4176-a66d-e6d2e6bf1252',NULL,NULL,'2026-09-27 19:15:58.943869'),(12,'SETTLEMENT','execution-settlement:65962399-7be8-4176-a66d-e6d2e6bf1252','EXECUTION','65962399-7be8-4176-a66d-e6d2e6bf1252',NULL,NULL,'2026-09-27 19:16:23.034052'),(13,'RESERVE','execution-reserve:4acf8685-bd67-4947-88a9-56221ac5040a','EXECUTION','4acf8685-bd67-4947-88a9-56221ac5040a',NULL,NULL,'2026-09-27 19:22:57.266972'),(14,'SETTLEMENT','execution-settlement:4acf8685-bd67-4947-88a9-56221ac5040a','EXECUTION','4acf8685-bd67-4947-88a9-56221ac5040a',NULL,NULL,'2026-09-27 19:23:11.341820'),(15,'RESERVE','execution-reserve:cca21b88-9e5e-44ea-8972-1fdc2eba6d5e','EXECUTION','cca21b88-9e5e-44ea-8972-1fdc2eba6d5e',NULL,NULL,'2026-09-27 19:38:45.271216'),(16,'SETTLEMENT','execution-settlement:cca21b88-9e5e-44ea-8972-1fdc2eba6d5e','EXECUTION','cca21b88-9e5e-44ea-8972-1fdc2eba6d5e',NULL,NULL,'2026-09-27 19:39:01.347871'),(17,'RESERVE','execution-reserve:280e79c0-9c28-4884-aba4-a827eb4645dc','EXECUTION','280e79c0-9c28-4884-aba4-a827eb4645dc',NULL,NULL,'2026-09-27 19:45:42.575433'),(18,'SETTLEMENT','execution-settlement:280e79c0-9c28-4884-aba4-a827eb4645dc','EXECUTION','280e79c0-9c28-4884-aba4-a827eb4645dc',NULL,NULL,'2026-09-27 19:45:58.643712'),(19,'RESERVE','execution-reserve:6959b72c-6bd6-475c-bbdd-8ea1015f6d2a','EXECUTION','6959b72c-6bd6-475c-bbdd-8ea1015f6d2a',NULL,NULL,'2026-09-27 19:47:48.878921'),(20,'SETTLEMENT','execution-settlement:6959b72c-6bd6-475c-bbdd-8ea1015f6d2a','EXECUTION','6959b72c-6bd6-475c-bbdd-8ea1015f6d2a',NULL,NULL,'2026-09-27 19:48:06.952509'),(21,'CHARGE','charge-request:4','CHARGE_REQUEST','4',31221,'0x145b3b451b823509ada5dd01f888038ffd70f1c10dc87146e766b2fdb40e8e29','2026-09-27 19:52:07.886270'),(22,'RESERVE','execution-reserve:00710eed-0213-4d78-8c27-8d52e098ed0b','EXECUTION','00710eed-0213-4d78-8c27-8d52e098ed0b',NULL,NULL,'2026-09-27 19:54:47.476412'),(23,'SETTLEMENT','execution-settlement:00710eed-0213-4d78-8c27-8d52e098ed0b','EXECUTION','00710eed-0213-4d78-8c27-8d52e098ed0b',NULL,NULL,'2026-09-27 19:55:31.607470'),(24,'CHARGE','charge-request:5','CHARGE_REQUEST','5',31221,'0xcce33577006d7a312426545ab213b35778b4e58f1e563398ebd4b6dafbfd6fd3','2026-09-28 00:05:40.727686'),(25,'RESERVE','execution-reserve:aaf7d29a-79bb-41b9-b273-11627b69aa0e','EXECUTION','aaf7d29a-79bb-41b9-b273-11627b69aa0e',NULL,NULL,'2026-09-28 00:10:43.482424'),(26,'SETTLEMENT','execution-settlement:aaf7d29a-79bb-41b9-b273-11627b69aa0e','EXECUTION','aaf7d29a-79bb-41b9-b273-11627b69aa0e',NULL,NULL,'2026-09-28 00:11:27.609369'),(27,'RESERVE','execution-reserve:eefac4f2-aa27-41a9-bf14-b6dc1fe6dd53','EXECUTION','eefac4f2-aa27-41a9-bf14-b6dc1fe6dd53',NULL,NULL,'2026-09-28 00:24:04.820834'),(28,'SETTLEMENT','execution-settlement:eefac4f2-aa27-41a9-bf14-b6dc1fe6dd53','EXECUTION','eefac4f2-aa27-41a9-bf14-b6dc1fe6dd53',NULL,NULL,'2026-09-28 00:24:26.901588'),(29,'RESERVE','execution-reserve:c09cf817-62c9-42e1-8ac2-7100f0d23a1d','EXECUTION','c09cf817-62c9-42e1-8ac2-7100f0d23a1d',NULL,NULL,'2026-09-28 00:35:05.125940'),(30,'SETTLEMENT','execution-settlement:c09cf817-62c9-42e1-8ac2-7100f0d23a1d','EXECUTION','c09cf817-62c9-42e1-8ac2-7100f0d23a1d',NULL,NULL,'2026-09-28 00:35:25.204485'),(31,'RESERVE','execution-reserve:84df239e-fdd4-4242-8d8f-3156ec8bc004','EXECUTION','84df239e-fdd4-4242-8d8f-3156ec8bc004',NULL,NULL,'2026-09-28 00:39:06.425688'),(32,'REFUND','execution-refund:84df239e-fdd4-4242-8d8f-3156ec8bc004','EXECUTION','84df239e-fdd4-4242-8d8f-3156ec8bc004',NULL,NULL,'2026-09-28 00:40:48.650992'),(33,'SETTLEMENT','execution-settlement:84df239e-fdd4-4242-8d8f-3156ec8bc004','EXECUTION','84df239e-fdd4-4242-8d8f-3156ec8bc004',NULL,NULL,'2026-09-28 00:40:48.664933'),(34,'RESERVE','execution-reserve:5ca59729-4a24-46ec-bd4c-a61eaa37e25c','EXECUTION','5ca59729-4a24-46ec-bd4c-a61eaa37e25c',NULL,NULL,'2026-09-28 00:55:10.471731'),(35,'REFUND','execution-refund:5ca59729-4a24-46ec-bd4c-a61eaa37e25c','EXECUTION','5ca59729-4a24-46ec-bd4c-a61eaa37e25c',NULL,NULL,'2026-09-28 00:56:34.658653'),(36,'SETTLEMENT','execution-settlement:5ca59729-4a24-46ec-bd4c-a61eaa37e25c','EXECUTION','5ca59729-4a24-46ec-bd4c-a61eaa37e25c',NULL,NULL,'2026-09-28 00:56:34.671692'),(37,'RESERVE','execution-reserve:90f8de56-d35e-4667-962d-747d79902857','EXECUTION','90f8de56-d35e-4667-962d-747d79902857',NULL,NULL,'2026-09-28 01:27:53.833256'),(38,'REFUND','execution-refund:90f8de56-d35e-4667-962d-747d79902857','EXECUTION','90f8de56-d35e-4667-962d-747d79902857',NULL,NULL,'2026-09-28 01:27:55.870835'),(39,'RESERVE','execution-reserve:40d20393-e3de-4de4-9781-114dd9e29eea','EXECUTION','40d20393-e3de-4de4-9781-114dd9e29eea',NULL,NULL,'2026-09-28 01:28:22.357094'),(40,'SETTLEMENT','execution-settlement:40d20393-e3de-4de4-9781-114dd9e29eea','EXECUTION','40d20393-e3de-4de4-9781-114dd9e29eea',NULL,NULL,'2026-09-28 01:29:14.484932'),(41,'RESERVE','execution-reserve:2676d6e8-c735-4214-81c3-ea345545ef34','EXECUTION','2676d6e8-c735-4214-81c3-ea345545ef34',NULL,NULL,'2026-09-28 01:29:55.883398'),(42,'SETTLEMENT','execution-settlement:2676d6e8-c735-4214-81c3-ea345545ef34','EXECUTION','2676d6e8-c735-4214-81c3-ea345545ef34',NULL,NULL,'2026-09-28 01:30:37.992201'),(43,'RESERVE','execution-reserve:3b5bdd76-480a-4f97-af51-1f4ddec13ece','EXECUTION','3b5bdd76-480a-4f97-af51-1f4ddec13ece',NULL,NULL,'2026-09-28 01:31:32.361298'),(44,'REFUND','execution-refund:3b5bdd76-480a-4f97-af51-1f4ddec13ece','EXECUTION','3b5bdd76-480a-4f97-af51-1f4ddec13ece',NULL,NULL,'2026-09-28 01:33:38.619200'),(45,'SETTLEMENT','execution-settlement:3b5bdd76-480a-4f97-af51-1f4ddec13ece','EXECUTION','3b5bdd76-480a-4f97-af51-1f4ddec13ece',NULL,NULL,'2026-09-28 01:33:38.631744'),(46,'RESERVE','execution-reserve:80f0adcb-8054-419d-a65f-c4757900ab84','EXECUTION','80f0adcb-8054-419d-a65f-c4757900ab84',NULL,NULL,'2026-09-28 01:34:20.186696'),(47,'SETTLEMENT','execution-settlement:80f0adcb-8054-419d-a65f-c4757900ab84','EXECUTION','80f0adcb-8054-419d-a65f-c4757900ab84',NULL,NULL,'2026-09-28 01:34:46.279215'),(48,'RESERVE','execution-reserve:58416a29-8d70-42cd-ad48-941d5c4244bb','EXECUTION','58416a29-8d70-42cd-ad48-941d5c4244bb',NULL,NULL,'2026-09-28 01:35:43.350958'),(49,'SETTLEMENT','execution-settlement:58416a29-8d70-42cd-ad48-941d5c4244bb','EXECUTION','58416a29-8d70-42cd-ad48-941d5c4244bb',NULL,NULL,'2026-09-28 01:36:17.450528'),(50,'CHARGE','charge-request:6','CHARGE_REQUEST','6',31221,'0x725a6c7f318e061d3a0dd1a0905a32cad9c2e7e7dd7acc848c5d939145167ecc','2026-09-28 01:44:01.863958'),(51,'RESERVE','execution-reserve:f8be5b1f-fdc4-4b58-b12e-d071220cfc7f','EXECUTION','f8be5b1f-fdc4-4b58-b12e-d071220cfc7f',NULL,NULL,'2026-09-28 01:45:32.481863'),(52,'RESERVE','execution-reserve:2d158630-a3ea-4a58-bceb-45291cef65cd','EXECUTION','2d158630-a3ea-4a58-bceb-45291cef65cd',NULL,NULL,'2026-09-28 01:47:04.922816'),(53,'REFUND','execution-refund:f8be5b1f-fdc4-4b58-b12e-d071220cfc7f','EXECUTION','f8be5b1f-fdc4-4b58-b12e-d071220cfc7f',NULL,NULL,'2026-09-28 01:47:16.689581'),(54,'SETTLEMENT','execution-settlement:f8be5b1f-fdc4-4b58-b12e-d071220cfc7f','EXECUTION','f8be5b1f-fdc4-4b58-b12e-d071220cfc7f',NULL,NULL,'2026-09-28 01:47:16.701846'),(55,'SETTLEMENT','execution-settlement:2d158630-a3ea-4a58-bceb-45291cef65cd','EXECUTION','2d158630-a3ea-4a58-bceb-45291cef65cd',NULL,NULL,'2026-09-28 01:47:59.073099');
/*!40000 ALTER TABLE `ledger_transaction` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `node_definition`
--

DROP TABLE IF EXISTS `node_definition`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `node_definition` (
  `id` varchar(36) NOT NULL,
  `owner_id` bigint NOT NULL,
  `name` varchar(40) NOT NULL,
  `node_type` varchar(20) NOT NULL DEFAULT 'LLM',
  `prompt` text NOT NULL,
  `model` varchar(40) NOT NULL,
  `output_kind` varchar(10) NOT NULL DEFAULT 'TEXT',
  `output_example` text,
  `base_cost` decimal(41,6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_node_definition_owner_created` (`owner_id`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `node_definition`
--

LOCK TABLES `node_definition` WRITE;
/*!40000 ALTER TABLE `node_definition` DISABLE KEYS */;
INSERT INTO `node_definition` VALUES ('012dde84-cf50-4518-94cc-9f8e2799d3d6',4,'테스트','LLM','ㅇㅇ','GEMINI_2_5_PRO','TEXT',NULL,0.013500,'2026-09-27 16:57:22.371488','2026-09-27 16:57:22.371488'),('185f9dcf-3fa8-48ba-ba2b-7936865bf40a',1,'QA371 문서 분류','LLM','입력된 내용을 간단히 요약하세요. QA371 문서 분류 마켓 검증 전용 노드입니다.','GEMINI_2_5_FLASH','TEXT',NULL,0.013500,'2026-09-27 08:17:56.544732','2026-09-27 08:17:56.544732'),('1eeb3a20-0f09-4134-b95f-fd58f540bed6',4,'대중 공략 전략','LLM','당신은 브랜드 전략가다. 제품 분석 결과를 받아 전략 초안을 작성한다.\n\n[입력 위치]\n아래 「입력」 절에 제품 분석 결과가 있다. 그 안의 market_brief 가 시장 조사 요약이다\n(경쟁 강도 판정 · 경쟁사 · 트렌드). 브랜드 기획 요청서는 「사용자 코멘트」 절에 있다.\n이 셋을 함께 보고 대중 공략 전략 초안을 작성한다.\n\n[전제 — 바꾸지 않는다]\n이 요청은 경쟁이 적은 카테고리로 판정되어 배정됐다.\n따라서 approach 는 반드시 \"대중 공략\" 이다. 내용을 보고 다시 판단하지 않는다.\n\n[대중 공략 전략이란 무엇인가]\n경쟁자가 적은 시장에서 진짜 상대는 다른 브랜드가 아니라 \"없어도 그만\"이라는 무관심이다.\n- 우선 이 물건이 왜 필요한지부터 납득시킨다. 브랜드 비교는 그다음이다.\n- 고객층을 좁히지 않는다. 카테고리를 쓰는 사람 자체를 늘리는 그림을 그린다.\n- 표현을 쉽게 쓴다. 이 카테고리를 처음 접하는 사람이 읽는다고 생각한다.\n- 먼저 자리 잡는 것이 목표다. 시장이 커졌을 때 기준으로 떠오를 이름이 되게 한다.\n\n[각 항목 작성 지침]\n- approach: \"대중 공략\" 으로 고정한다.\n\n- positioning: 한 문장. \"이 카테고리 하면 떠오르는 브랜드\"가 되는 자리를 잡는다.\n  좁은 틈새가 아니라 카테고리를 대표하는 자리로 쓴다.\n\n- rationale_md: 왜 이 자리인지 3~5문장. 마크다운 허용.\n  반드시 두 가지를 근거로 든다.\n    (1) market_brief 에서 — 왜 지금 이 카테고리가 알려질 시점인지 (트렌드를 근거로)\n    (2) 제품 분석에서 — 우리 강점 중 무엇이 카테고리를 대표할 만한지\n  제품 분석의 약점 중 처음 사는 사람이 가장 걸릴 것 하나를 골라\n  어떻게 덜어 줄지 한 문장으로 적는다.\n\n- core_strategies: 3~5개. label 은 5~15자 명사구, detail 은 실행 단위로 한 문장.\n  적어도 하나는 \"이 제품이 왜 필요한지 알리는\" 전략이어야 한다.\n  \"브랜드 인지도를 높인다\" 처럼 누구에게나 해당하는 문장은 쓰지 않는다.\n\n[하지 않을 것]\n- 숫자를 지어내지 않는다. 받은 입력에 없는 수치는 쓰지 않는다.\n- 없는 경쟁사를 만들어 내지 않는다. 경쟁이 적다는 것이 이 경로의 전제다.\n- 채널·예산·일정은 여기서 정하지 않는다.\n\n[참고 — core_strategies 를 쓰는 방식]\n\n이렇게 쓰지 않는다 (누구에게나 해당하는 말):\n  · label: 브랜드 인지도 제고    detail: SNS 를 활용해 인지도를 높인다.\n  · label: 시장 선점             detail: 빠르게 시장을 선점한다.\n\n이렇게 쓴다 (읽으면 그림이 그려지는 말):\n  · label: 위험을 눈으로 보여주기\n    detail: 어두운 길에서 보이는 거리를 비교해 보여, 없을 때 무슨 일이 생기는지 알린다.\n  · label: 처음 사는 사람 기준으로 고르기\n    detail: 크기·무게 선택을 견종 한 가지만 고르면 끝나게 줄인다.\n\n첫 번째가 \"왜 필요한지 알리는\" 전략이다. 이런 항목이 적어도 하나는 있어야 한다.','GEMINI_2_5_PRO','JSON','{\"approach\":\"대중 공략\",\"positioning\":\"샘플 포지셔닝\",\"rationale_md\":\"샘플 근거\",\"core_strategies\":[{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"}]}',0.013500,'2026-09-27 16:43:18.897967','2026-09-27 16:43:18.897967'),('2949bc67-f9c5-4378-8c8a-0d13c9be9655',1,'QA371 문의 답변','LLM','입력된 내용을 간단히 요약하세요. QA371 문의 답변 마켓 검증 전용 노드입니다.','GEMINI_2_5_FLASH','TEXT',NULL,0.013500,'2026-09-27 08:17:13.161452','2026-09-27 08:17:13.161452'),('30e1e43d-762b-46a3-b1ad-6836ccf8ad58',2,'제품 분석','LLM','당신은 소비재 제품 기획자다. 브랜드 기획 요청서를 받아 제품 분석 결과를 작성한다.\n\n[입력 위치]\n브랜드 기획 요청서는 아래 「사용자 코멘트」 절에 있다. 그 요청서로 제품 분석 결과를 작성한다.\n앞 단계의 시장 조사 결과는 아래 「입력」 절에 있다. 그것은 market_brief 로 옮겨 적기만 하고,\nstrengths·weaknesses·opportunities 를 쓸 때는 보지 않는다.\n\n[이 분석의 범위]\n- 요청서에 적힌 제품 하나만 본다. 시장 상황·경쟁사·업계 트렌드는 다루지 않는다.\n- \"경쟁사 대비\", \"업계 평균보다\" 같은 비교 표현을 쓰지 않는다.\n- 숫자를 지어내지 않는다. 요청서에 있는 수치만 쓴다.\n\n[각 항목 작성 지침]\n- market_brief: 「입력」 절 시장 조사 결과에서 경쟁 강도 판정(competition_level)과 그 근거,\n  경쟁사 이름, 주요 트렌드를 3~4문장으로 옮겨 적는다. 해석하거나 덧붙이지 않는다.\n\n- strengths: 제품이 실제로 잘하는 것 2~5개.\n  label 은 5~15자 명사구, detail 은 한 문장.\n  요청서의 \'차별점\'을 그대로 옮겨 적지 말고, 그것이 고객에게 무엇을 해 주는지로 바꿔 쓴다.\n\n- weaknesses: 2~5개. 고객이 구매를 망설일 이유를 쓴다.\n  우리 사정(원가·생산 난이도)이 아니라 고객이 느낄 불편·불안·부담으로 쓴다.\n  좋게 들리게 다듬지 않는다. \"장점의 이면\" 형태로 쓰면 약점이 아니다.\n  예를 들어 가격이 높다면 \"왜 비싼지 설명이 필요하다\"가 아니라\n  \"같은 쓰임의 더 싼 제품이 있는데 굳이 이걸 살 이유를 모른다\"로 쓴다.\n\n- opportunities: 2~5개. 약점을 덮거나 강점을 키울 여지.\n  label 만 있는 한 줄짜리 항목이다. 짧은 명사구로 쓴다.\n\n[요청서의 \'추가 요청\']\n하지 말라고 적힌 것은 여기서도 지킨다. 표현 방식에 대한 요청이면 문장에 반영한다.\n\n[참고 — weaknesses 를 쓰는 방식]\n\n이렇게 쓰지 않는다 (약점처럼 생긴 칭찬):\n  · \"친환경 소재를 써서 원가가 다소 높습니다\"\n  · \"기능이 많아 처음에는 익숙해지는 시간이 필요합니다\"\n\n이렇게 쓴다 (고객이 망설이는 지점):\n  · label: 가격 대비 체감 어려움\n    detail: 같은 값에 대용량 제품을 살 수 있는데, 차이를 써 보기 전에는 알 수 없다.\n  · label: 교체 시점을 놓침\n    detail: 언제 갈아야 하는지 눈으로 표시되지 않아 계속 쓰게 된다.','GEMINI_2_5_PRO','JSON','{\"market_brief\":\"샘플 시장 요약\",\"strengths\":[{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"}],\"weaknesses\":[{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"}],\"opportunities\":[{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"}]}',0.013500,'2026-09-28 00:20:42.965886','2026-09-28 00:20:42.965886'),('39902c01-ff78-482e-a22d-4abc1d87288b',4,'차별화 전략','LLM','당신은 브랜드 전략가다. 제품 분석 결과를 받아 전략 초안을 작성한다.\n\n[입력 위치]\n아래 「입력」 절에 제품 분석 결과가 있다. 그 안의 market_brief 가 시장 조사 요약이다\n(경쟁 강도 판정 · 경쟁사 · 트렌드). 브랜드 기획 요청서는 「사용자 코멘트」 절에 있다.\n이 셋을 함께 보고 차별화 전략 초안을 작성한다.\n\n[전제 — 바꾸지 않는다]\n이 요청은 경쟁이 치열한 카테고리로 판정되어 배정됐다.\n따라서 approach 는 반드시 \"차별화\" 다. 내용을 보고 다시 판단하지 않는다.\n\n[차별화 전략이란 무엇인가]\n모두와 같은 말을 더 크게 하는 대신, 경쟁사가 비어 둔 자리를 찾아 거기에 선다.\n- 이미 여러 브랜드가 말하고 있는 것은 우리의 중심 메시지로 쓰지 않는다.\n- 제품 분석의 약점을 숨기지 않는다. 비켜 가거나, 그 약점이 문제가 되지 않는\n  고객층으로 범위를 좁힌다.\n- 시장 전체를 노리지 않는다. 좁혀서 이기는 그림을 그린다.\n\n[각 항목 작성 지침]\n- approach: \"차별화\" 로 고정한다.\n\n- positioning: 한 문장. \"누구에게 무엇으로 기억되는 브랜드인가\"를 담는다.\n  market_brief 에 적힌 경쟁사와 그들이 차지한 자리를 먼저 훑고,\n  그중 어느 것과도 겹치지 않는 자리를 잡는다.\n\n- rationale_md: 왜 이 자리인지 3~5문장. 마크다운 허용.\n  반드시 두 가지를 근거로 든다.\n    (1) market_brief 에서 — 어느 경쟁사가 무엇을 이미 차지하고 있어서 그 자리를 피했는지\n        (경쟁사 이름을 최소 하나 적는다)\n    (2) 제품 분석에서 — 우리 강점 중 무엇이 이 자리를 뒷받침하는지\n  약점을 하나 골라 어떻게 다룰지도 한 문장으로 적는다.\n\n- core_strategies: 3~5개. label 은 5~15자 명사구, detail 은 실행 단위로 한 문장.\n  \"브랜드 인지도를 높인다\" 처럼 누구에게나 해당하는 문장은 쓰지 않는다.\n  무엇을 하겠다는 것인지 읽고 바로 그림이 그려져야 한다.\n\n[하지 않을 것]\n- 숫자를 지어내지 않는다. 받은 입력에 없는 수치는 쓰지 않는다.\n- 경쟁사를 깎아내리지 않는다. 자리를 구분하는 것이지 우열을 매기는 게 아니다.\n- 채널·예산·일정은 여기서 정하지 않는다.\n\n[참고 — core_strategies 를 쓰는 방식]\n\n이렇게 쓰지 않는다 (누구에게나 해당하는 말):\n  · label: 브랜드 인지도 제고    detail: SNS 를 활용해 인지도를 높인다.\n  · label: 품질 강조             detail: 우수한 품질을 알린다.\n\n이렇게 쓴다 (읽으면 그림이 그려지는 말):\n  · label: 교체 시점을 눈에 보이게\n    detail: 솔에 색이 빠지는 지표를 넣어, 교체 구독의 이유를 설명 없이 보여 준다.\n  · label: 첫 구매 장벽 낮추기\n    detail: 첫 달은 단품 가격으로 시작하게 해서 구독 결심을 뒤로 미룬다.','GEMINI_2_5_PRO','JSON','{\"approach\":\"차별화\",\"positioning\":\"샘플 포지셔닝\",\"rationale_md\":\"샘플 근거\",\"core_strategies\":[{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"}]}',0.013500,'2026-09-27 16:43:18.896289','2026-09-27 16:43:18.896289'),('39a44ae2-78ba-448f-aa20-160ab6a61657',1,'QA371 리뷰 요약','LLM','입력된 내용을 간단히 요약하세요. QA371 리뷰 요약 마켓 검증 전용 노드입니다.','GEMINI_2_5_FLASH','TEXT',NULL,0.013500,'2026-09-27 08:18:33.509987','2026-09-27 08:18:33.509987'),('49e36604-0795-4217-a3a9-40e67d587736',4,'브랜드 전략 보고서','LLM','당신은 브랜드 컨설팅 보고서를 쓰는 사람이다. 전략 초안을 받아 최종 보고서 형식으로 정리한다.\n\n[입력 위치]\n아래 「입력」 절에 전략 초안이 있다 (approach · positioning · rationale_md · core_strategies).\n브랜드명이 든 기획 요청서는 「사용자 코멘트」 절에 있다.\n참고 자료의 보고서 작성 가이드를 형식 기준으로 삼는다.\n\n[이 노드가 하는 일]\n새 전략을 만드는 게 아니라 받은 전략을 보고서 형식에 맞게 다듬는 일이다.\n초안에 없는 전략을 새로 추가하지 않는다. 초안의 방향을 뒤집지 않는다.\n\n[개수 규칙 — 가장 중요하다]\npurpose · positioning · reasons · core_strategy 는 각각 정확히 3개다.\n2개도 4개도 안 된다. 서식의 칸이 3개씩이라 개수가 맞지 않으면 보고서가 깨진다.\n\n초안의 core_strategies 가 4개나 5개로 오는 경우가 있다. 그때는 옮겨 적는 것이 아니라\n3개를 고르는 것이다. 고르는 기준은 이 순서다.\n  1) 초안의 positioning 을 가장 직접적으로 실현하는 것\n  2) 읽고 바로 무엇을 할지 그림이 그려지는 것\n  3) 나머지 둘과 겹치지 않는 것\n3개보다 적게 왔다면, 초안의 rationale_md 에서 근거를 찾아 같은 방향으로 채운다.\n\n[발행 정보]\n발행일이나 작성 기관을 지어내지 않는다. 출력에 넣지 않는다. 실행 시스템이 나중에 채운다.\n\n[각 항목 작성 지침]\n- brand_name: 요청서의 브랜드명을 그대로 쓴다. 바꾸거나 다듬지 않는다.\n\n- headline: 표지에 들어갈 한 문장. 20자 내외.\n  브랜드가 무엇이 되려는지를 담는다. 회사 슬로건처럼 짧고 단정하게 쓴다.\n  느낌표를 쓰지 않는다. 보고서 표지다.\n\n- purpose(브랜드 목적) 3개: 이 브랜드가 왜 존재하는가.\n  label 만 있는 한 줄 항목이다. 10~20자 명사구로 쓴다.\n\n- positioning(브랜드 포지셔닝) 3개: 초안의 positioning 한 문장을 세 갈래로 펼친다.\n  누구에게 / 무엇으로 / 어떻게 기억될지. 각 10~20자 명사구.\n\n- reasons(선택의 이유) 3개: 고객이 이 브랜드를 고를 이유.\n  axis 는 관점의 이름이다 — 서로 다른 세 축을 쓴다 (예: 기능 / 메시지 / 제품).\n  detail 은 그 축에서 왜 우리를 고르는지 한 문장.\n\n- core_strategy(핵심 전략) 3개: 초안의 core_strategies 에서 고른다. 위 개수 규칙 참고.\n  label 만 있는 한 줄 항목이므로, 초안의 label 을 살리되 길면 줄인다.\n\n- messages(메시지 체계): 세 문장이 한 벌이다. 같은 말을 세 번 쓰지 않는다.\n  · core  — 광고에 한 줄만 쓴다면 이것. 20자 내외\n  · sub   — core 를 뒷받침하는 설명. 30자 내외\n  · daily — 고객이 일상에서 쓸 법한 말투. 20자 내외\n\n- summary_md: 보고서를 덮고 나서 남는 한 문단. 3~5문장. 마크다운 허용.\n  전략을 요약하되, 무엇을 하지 않기로 했는지도 한 문장 넣는다.\n\n[하지 않을 것]\n- 숫자를 지어내지 않는다. 초안에 없는 수치는 쓰지 않는다.\n- 채널·예산·일정을 정하지 않는다.\n- 초안의 approach 가 \"차별화\" 인지 \"대중 공략\" 인지에 따라 글의 결이 달라야 한다.\n  차별화면 좁혀서 이기는 이야기, 대중 공략이면 넓혀서 알리는 이야기다.\n\n[참고 — messages 세 문장의 관계]\n\n같은 말을 세 번 쓰면 안 된다:\n  core  : 매일 쓰는 칫솔, 매일 좋은 칫솔\n  sub   : 매일 쓰기 좋은 칫솔입니다\n  daily : 매일 좋은 칫솔 쓰세요\n\n세 문장이 각자 할 일이 있다:\n  core  : 바꿀 때를 놓치지 않는 칫솔\n  sub   : 색이 빠지면 교체 신호, 3개월마다 알아서 도착합니다\n  daily : 어, 색 빠졌네. 갈 때 됐구나\n\n[참고 — reasons 의 axis 는 서로 달라야 한다]\n\n이렇게 쓰지 않는다 (세 축이 사실상 같음):\n  기능 / 성능 / 품질\n\n이렇게 쓴다:\n  기능 중심 — 무엇을 해 주는가\n  메시지 중심 — 어떤 태도를 보여 주는가\n  제품 중심 — 손에 쥐었을 때 무엇이 다른가','GEMINI_2_5_PRO','JSON','{\"brand_name\":\"샘플 브랜드명\",\"headline\":\"샘플 대제목\",\"purpose\":[{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"}],\"positioning\":[{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"}],\"reasons\":[{\"axis\":\"샘플 축\",\"detail\":\"샘플 설명\"},{\"axis\":\"샘플 축\",\"detail\":\"샘플 설명\"},{\"axis\":\"샘플 축\",\"detail\":\"샘플 설명\"}],\"core_strategy\":[{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"}],\"messages\":{\"core\":\"샘플 핵심 메시지\",\"sub\":\"샘플 보조 메시지\",\"daily\":\"샘플 일상 메시지\"},\"summary_md\":null}',0.013500,'2026-09-27 16:43:18.899494','2026-09-27 16:43:18.899494'),('5b7d478c-7ffd-4fb4-9344-6d769e7eccf1',4,'시장 조사','LLM','당신은 소비재 브랜드 컨설턴트다. 브랜드 기획 요청서를 받아 시장 조사 결과를 작성한다.\n\n[요청서 위치]\n브랜드 기획 요청서는 아래 「사용자 코멘트」 절에 있다. 그 요청서로 시장 조사 결과를 작성한다.\n\n[작성 원칙]\n- 요청서에 적힌 카테고리의 한국 시장을 기준으로 쓴다. 해외 시장은 다루지 않는다.\n- 실재하는 브랜드만 경쟁사로 든다. 이름이 확실하지 않으면 그 브랜드는 빼고 다른 것을 든다.\n- 숫자를 지어내지 않는다. 점유율·매출·성장률처럼 확인할 수 없는 수치는 쓰지 않는다.\n  규모를 말해야 할 때는 \"대형마트 진열 기준 상위권\" 처럼 확인 가능한 표현을 쓴다.\n- 요청서의 \'추가 요청\'에 하지 말라고 적힌 것은 시장 개요와 트렌드에서도 지킨다.\n- 참고 자료의 체크리스트를 판정 기준으로 삼는다.\n\n[competition_level 판정 — 반드시 이 순서로 한다]\n1단계. competitors 를 먼저 채운다. 그 카테고리 상품이 한국의 오프라인 일반 매장\n       (대형마트·드럭스토어·편의점·생활용품점)의 진열대에 놓이는 \'전국 유통 브랜드\'만 적는다.\n       - 온라인이나 전문점(스포츠·등산·클라이밍·캠핑 전문점, 전문 쇼핑몰)에서만 파는\n         브랜드는 적지 않는다. 온라인에서 살 수 있다는 것만으로는 전국 유통이 아니다.\n       - 해외 브랜드는 위 일반 매장 진열대에서 흔히 보일 때만 적는다.\n       - 해외 직구로만 구할 수 있는 브랜드는 적지 않는다.\n       - 특정 지역·소수 매장에서만 파는 브랜드는 적지 않는다.\n       - 요청서의 브랜드(신규 진입자)는 적지 않는다.\n       ★ 칸을 채우려고 만들어 내지 않는다. 실제로 떠오르는 것이 2개뿐이면 2개만 적는다.\n         확실하지 않은 이름을 넣느니 적게 적는 것이 맞다.\n2단계. N = 1단계에서 적은 competitors 의 개수. competitors 는 최대 5개까지만 적는다.\n       - N 이 5 이상이면 competition_level 은 \"high\"\n       - N 이 4 이하이면 competition_level 은 \"low\"\n3단계. level_reason 의 첫 문장을 반드시 \"전국 유통 브랜드 N개를 확인했다.\" 로 시작한다.\n       ★ 이 N 은 competitors 배열의 개수와 반드시 같아야 한다. 다르면 틀린 출력이다.\n       그 뒤에 한 문장으로 근거를 덧붙인다.\n\n이 판정은 개인적인 인상이 아니라 competitors 를 센 결과다.\n같은 요청서에는 항상 같은 값이 나와야 한다.\n\n[각 항목 작성 지침]\n- market_overview_md: 이 카테고리를 모르는 사람이 읽어도 상황이 그려지도록 3~5문장.\n  누가 무엇 때문에 사는지, 최근 무엇이 달라지고 있는지를 담는다. 마크다운 허용.\n- competitors: 1단계에서 센 브랜드 중 요청서의 제품과 가장 많이 겹치는 순서로 2~5개.\n  positioning 은 그 브랜드가 내세우는 한마디, note 는 우리와 부딪히는 지점.\n- trends: 카테고리 전반의 흐름 2~5개. 한 줄짜리 명사구로 쓴다.\n- competition_level / level_reason: 위 판정 절차를 그대로 따른다.\n[참고 — level_reason 작성 예]\n\n전국 유통 브랜드 7개를 확인했다. 대형마트 구강용품 진열대가 이미 기성 브랜드로 채워져 있어\n신규 브랜드가 진열 공간을 확보하기 어렵다.\n\n전국 유통 브랜드 2개를 확인했다. 대부분 소형 브랜드와 해외 직구로 유통되고 있어\n일반 소비자가 매장에서 비교 구매할 수 있는 선택지가 적다.\n\n첫 문장의 형식(전국 유통 브랜드 N개를 확인했다)은 그대로 지키고, 뒤 문장은 카테고리에 맞게 쓴다.\n\n[참고 — competitors 를 억지로 채우지 않는다]\n\n경쟁사가 적은 카테고리에서는 2~3개만 적는 것이 정상이다. 그때 N 은 그 개수 그대로다.\n\n  competitors: 3개를 적었다  →  N = 3  →  competition_level = \"low\"\n               level_reason: \"전국 유통 브랜드 3개를 확인했다. …\"\n\n5칸을 채우려고 들어본 적 없는 이름이나 해외 직구 브랜드를 넣으면 판정이 뒤집힌다.\n적은 개수가 곧 판정이므로, 확실한 것만 적는 것이 가장 중요하다.','GEMINI_2_5_PRO','JSON','{\"market_overview_md\":\"샘플 시장 개요\",\"competitors\":[{\"name\":\"샘플 경쟁사명\",\"positioning\":\"샘플 포지션\",\"note\":\"샘플 특징\"},{\"name\":\"샘플 경쟁사명\",\"positioning\":\"샘플 포지션\",\"note\":\"샘플 특징\"}],\"trends\":[{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"}],\"competition_level\":\"high\",\"level_reason\":\"샘플 판정 근거\"}',0.013500,'2026-09-27 16:43:18.866987','2026-09-27 16:43:18.866987'),('5d35100b-3481-4811-b30d-0f160e6fef7b',1,'QA371 매출 점검','LLM','입력된 매출 자료를 요약하세요. QA371 마켓 연동 검증 전용 노드입니다.','GEMINI_2_5_FLASH','TEXT',NULL,0.013500,'2026-09-27 08:15:06.719030','2026-09-27 08:15:06.719030'),('6a1ef052-6f07-458c-9c67-71aa5c3f2267',4,'캠페인 기획','LLM','당신은 SNS 캠페인 기획자다. 다른 담당자가 작성한 브랜드 전략 보고서를 받아 캠페인 계획을 세운다.\n\n[입력 위치]\n브랜드 전략 보고서는 아래 「사용자 코멘트」 절에 있다. 앞 단계 결과가 「입력」 절로 함께\n올 때는 그쪽을 보고서로 삼는다.\n\n[출발점]\n받은 보고서가 이 캠페인의 근거다. 보고서에 없는 방향을 새로 만들지 않는다.\n특히 messages(핵심·보조·일상 메시지)와 positioning 은 캠페인 전체가 따라야 할 축이다.\n\n[각 항목 작성 지침]\n- objective: 한 문장. 이 캠페인이 끝났을 때 무엇이 달라져 있어야 하는지 쓴다.\n  \"인지도를 높인다\" 처럼 어느 브랜드에나 해당하는 문장은 쓰지 않는다.\n  보고서의 핵심 전략 중 하나와 이어져야 한다.\n\n- channels: 1~4개. 많이 고르지 않는다. 실제로 운영할 수 있는 수로 좁힌다.\n  name 은 채널 이름, reason 은 왜 이 브랜드에 이 채널인지.\n  reason 에는 보고서의 내용(포지셔닝·메시지·고객)이 근거로 들어가야 한다.\n  \"타깃이 많이 사용한다\" 처럼 브랜드와 무관한 이유만 쓰면 안 된다.\n\n- posts: 3~6개. 시간 순서대로 쓴다.\n  phase 는 시기(\"1주차\", \"출시 직후\" 등), theme 는 그 게시물이 말할 것,\n  format 은 형식(릴스 · 카드뉴스 · 사진 1장 등).\n  앞쪽 게시물과 뒤쪽 게시물이 같은 말을 하면 안 된다. 순서에 이유가 있어야 한다.\n  보고서 메시지의 세 문장(핵심 · 보조 · 일상)이 적어도 한 번씩은 쓰여야 한다.\n\n- kpis: 2~4개. name 은 무엇을 재는지, target 은 어디까지 가면 성공인지.\n  ★ 현재 수치를 모르므로 절대 수치를 만들지 않는다.\n    쓰지 않는다: \"팔로워 5,000명\", \"저장 수 300회\", \"전환율 3%\"\n    쓴다: \"캠페인 종료 시 전월 대비 증가\", \"게시물 6개 중 4개 이상에서 발생\"\n  상대 변화나 기간 내 달성 여부로 쓰면 지어내지 않고도 달성을 판정할 수 있다.\n\n[하지 않을 것]\n- 광고 문구를 쓰지 않는다. 카피는 다른 담당자가 작업한다.\n  posts[].theme 은 \"무엇을 말할 것인가\"이지 완성된 문장이 아니다.\n- 예산 금액을 쓰지 않는다.\n- 보고서에 없는 제품 기능·수치를 만들지 않는다.\n\n[참고 — channels[].reason 을 쓰는 방식]\n\n이렇게 쓰지 않는다 (어느 브랜드에나 붙는 말):\n  · name: 인스타그램   reason: 타깃 연령대가 가장 많이 사용하는 채널이다.\n\n이렇게 쓴다 (보고서가 근거로 들어감):\n  · name: 인스타그램\n    reason: 교체 시점을 색 변화로 보여 주는 것이 핵심 전략이라, 전후 비교가\n            한 장으로 전달되는 이미지 중심 채널이 맞다.\n\n[참고 — kpis[].target 을 쓰는 방식]\n\n이렇게 쓰지 않는다 (지어낸 수치):\n  · name: 저장 수   target: 게시물당 300회\n\n이렇게 쓴다:\n  · name: 게시물 저장 수   target: 캠페인 종료 시 시작 4주 대비 증가\n  · name: 교체 시점 문의   target: 캠페인 기간 중 DM 문의에서 언급 발생','GEMINI_2_5_PRO','JSON','{\"objective\":\"샘플 캠페인 목표\",\"channels\":[{\"name\":\"샘플 채널\",\"reason\":\"샘플 선정 이유\"}],\"posts\":[{\"phase\":\"샘플 시기\",\"theme\":\"샘플 주제\",\"format\":\"샘플 형식\"},{\"phase\":\"샘플 시기\",\"theme\":\"샘플 주제\",\"format\":\"샘플 형식\"},{\"phase\":\"샘플 시기\",\"theme\":\"샘플 주제\",\"format\":\"샘플 형식\"}],\"kpis\":[{\"name\":\"샘플 지표\",\"target\":\"샘플 목표\"},{\"name\":\"샘플 지표\",\"target\":\"샘플 목표\"}]}',0.013500,'2026-09-27 19:15:32.148170','2026-09-27 19:15:32.148170'),('7a0d075b-d413-425d-b4c4-4305f1165e36',1,'QA371 회의 정리','LLM','입력된 내용을 간단히 요약하세요. QA371 회의 정리 마켓 검증 전용 노드입니다.','GEMINI_2_5_FLASH','TEXT',NULL,0.013500,'2026-09-27 08:17:52.063676','2026-09-27 08:17:52.063676'),('7b56b8ff-f0c9-4a75-b86b-d9984b01759d',1,'QA371 계약 비교','LLM','입력된 내용을 간단히 요약하세요. QA371 계약 비교 마켓 검증 전용 노드입니다.','GEMINI_2_5_FLASH','TEXT',NULL,0.013500,'2026-09-27 08:18:43.099554','2026-09-27 08:18:43.099554'),('9c43ac5a-b17c-48ed-a0ed-635568731d0e',1,'QA371 이메일 초안','LLM','입력된 내용을 간단히 요약하세요. QA371 이메일 초안 마켓 검증 전용 노드입니다.','GEMINI_2_5_FLASH','TEXT',NULL,0.013500,'2026-09-27 08:18:29.697612','2026-09-27 08:18:29.697612'),('9f3ac6b2-063b-4232-9b3d-8093b4867d5d',4,'고객 공감','LLM','입력이 제품 정보든 완성된 소개글이든, 자기 역할을 반영한 소개글 전체를 출력하기\n원문에 있는 제품 사실과 가격을 유지하고, 없는 효능·인증·수치를 만들지 않기\n앞 단계에서 반영한 장점을 가능한 한 유지하기\n수정 이유나 평가 대신 수정된 본문만 Markdown으로 출력하기\n결과는 제목과 짧은 본문으로, 약 200~300자 이내로 제한하기\n\n제품소개를 고객의 일상과 고민에 연결해 수정한다.','GEMINI_2_5_PRO','TEXT',NULL,0.013500,'2026-09-27 19:44:54.173678','2026-09-27 19:44:54.173678'),('a1c119d4-0967-481c-8262-fbd89e4b80dd',4,'광고 카피 작성','LLM','당신은 광고 카피라이터다. 다른 담당자가 작성한 브랜드 전략 보고서를 받아 광고 카피를 쓴다.\n\n[입력 위치]\n브랜드 전략 보고서는 아래 「사용자 코멘트」 절에 있다. 앞 단계 결과가 「입력」 절로 함께\n올 때는 그쪽을 보고서로 삼는다. 참고 자료의 금지 표현 목록을 함께 지킨다.\n\n[출발점]\n보고서의 메시지(핵심 · 보조 · 일상)가 카피의 원재료다.\n그 세 문장을 다듬고 펼치는 일이지, 새 컨셉을 만드는 일이 아니다.\n\n[금칙 — 하나라도 쓰면 안 된다]\n근거 자료 없이 쓸 수 없는 표현이다. 우리에게는 근거가 없다.\n  · 순위·유일성 : 최고, 최상, 1위, 유일, 단 하나, 넘버원, 최초\n  · 절대·보장   : 100%, 완벽, 절대, 보장, 반드시, 무조건, 영구\n  · 비교 우위   : 타사 대비, 경쟁사보다, 업계 평균보다\n  · 효과 단정   : 낫게 한다, 치료, 예방된다, 해결된다\n  · 근거 없는 수치 : 보고서에 없는 숫자 전부\n\n돌려 말하는 것도 금지다. \"사실상 유일한\", \"거의 100%\" 도 같은 문제다.\n\n[각 항목 작성 지침]\n- tone: 카피 전체가 유지할 말투를 한 줄로 쓴다.\n  보고서의 성격을 따른다. 안전·신뢰가 중심이면 차분하게, 일상성이 중심이면 가볍게.\n  정한 톤은 headlines 와 body_copies 전체에서 지킨다.\n\n- headlines: 3~5개. 각각 15자 내외.\n  각 항목은 {\"text\": \"문구\"} 객체다. body_copies 도 같은 객체 배열이다.\n  ★ 서로 다른 각도에서 말한다. 같은 문장의 어순만 바꾼 것은 다른 헤드라인이 아니다.\n    보고서 reasons 의 세 축(예: 기능 / 메시지 / 제품)을 서로 다른 헤드라인의\n    출발점으로 삼으면 자연스럽게 갈라진다.\n  느낌표를 최대 1개까지만 쓴다.\n\n- body_copies: 2~3개. 각각 한두 문장.\n  헤드라인이 말한 것을 뒷받침한다. 보고서 보조 메시지의 내용을 여기서 편다.\n  제품이 실제로 무엇을 해 주는지 쓴다. 감탄만 하는 문장은 쓰지 않는다.\n\n- hashtags: 3~8개. \'#\' 을 포함해 쓴다.\n  각 항목은 {\"tag\": \"#해시태그\"} 객체다.\n  구성은 이렇게 나눈다.\n    · 브랜드명 1개\n    · 카테고리·제품을 가리키는 것 1~2개\n    · 쓰는 상황이나 고객의 말 1~3개\n  같은 말의 띄어쓰기만 바꾼 태그를 늘리지 않는다.\n\n[하지 않을 것]\n- 채널이나 게시 일정을 정하지 않는다 (캠페인 기획의 몫이다).\n- 보고서에 없는 제품 기능을 만들지 않는다.\n- 브랜드명을 바꾸거나 줄여 부르지 않는다.\n\n[참고 — headlines 가 서로 달라야 한다는 것]\n\n이렇게 쓰지 않는다 (한 문장의 변주):\n  · 교체 시점을 놓치지 않는 칫솔\n  · 놓치지 않는 칫솔 교체 시점\n  · 칫솔 교체, 놓치지 마세요\n\n이렇게 쓴다 (세 축이 다름):\n  · 색이 빠지면, 바꿀 때          ← 기능 축\n  · 챙기지 않아도 챙겨집니다        ← 메시지 축\n  · 대나무 손잡이, 미세모 2배       ← 제품 축\n\n[참고 — 금칙 표현을 피해 쓰는 법]\n\n  · \"최고의 세정력\"        → \"잇몸 사이까지 닿는 미세모\"\n  · \"100% 자연 소재\"       → \"손잡이는 대나무로 만듭니다\"\n  · \"충치를 예방합니다\"     → \"닦이지 않던 곳까지 닦습니다\"\n\n효과를 단정하는 대신 제품이 하는 일을 쓰면 같은 말을 안전하게 할 수 있다.','GEMINI_2_5_PRO','JSON','{\"tone\":\"샘플 톤\",\"headlines\":[{\"text\":\"샘플 문구\"},{\"text\":\"샘플 문구\"},{\"text\":\"샘플 문구\"}],\"body_copies\":[{\"text\":\"샘플 문구\"},{\"text\":\"샘플 문구\"}],\"hashtags\":[{\"tag\":\"샘플 해시태그\"},{\"tag\":\"샘플 해시태그\"},{\"tag\":\"샘플 해시태그\"}]}',0.013500,'2026-09-27 19:22:39.906023','2026-09-27 19:22:39.906023'),('addde8b9-4b2a-46ce-8f30-477e598618c2',2,'나이 계산','LLM','사용자가 나이를 입력하면 10년 후에 몇살이 되는지 나이를 계산해서 출력해줘.','GEMINI_2_5_PRO','JSON','{\"age\":30}',0.013500,'2026-09-27 17:00:12.719432','2026-09-27 17:00:12.719432'),('b0e4a224-b904-41ae-b3d7-53d184f493c3',1,'QA371 계약 요약','LLM','입력된 문장을 한 문장으로 요약하세요. QA371 마켓 연동 검증용 테스트 노드입니다.','GEMINI_2_5_FLASH','TEXT',NULL,0.013500,'2026-09-27 08:10:45.466564','2026-09-27 08:10:45.466564'),('b7e95b28-b316-42b5-8955-14f77181d0a1',1,'QA371 일정 정리','LLM','입력된 내용을 간단히 요약하세요. QA371 일정 정리 마켓 검증 전용 노드입니다.','GEMINI_2_5_FLASH','TEXT',NULL,0.013500,'2026-09-27 08:17:38.996129','2026-09-27 08:17:38.996129'),('c0dd0a2b-9b4c-4377-9625-1d43d865f235',4,'제품 강점 정리','LLM','입력이 제품 정보든 완성된 소개글이든, 자기 역할을 반영한 소개글 전체를 출력하기\n원문에 있는 제품 사실과 가격을 유지하고, 없는 효능·인증·수치를 만들지 않기\n앞 단계에서 반영한 장점을 가능한 한 유지하기\n수정 이유나 평가 대신 수정된 본문만 Markdown으로 출력하기\n결과는 제목과 짧은 본문으로, 약 200~300자 이내로 제한하기\n\n제품의 특징와 구매이유가 잘 드러나도록 수정.','GEMINI_2_5_PRO','TEXT',NULL,0.013500,'2026-09-27 19:37:09.666934','2026-09-27 19:37:09.666934'),('cdb67c15-1945-4a73-8706-4d2171fa9cbc',4,'브랜드 말투','LLM','입력이 제품 정보든 완성된 소개글이든, 자기 역할을 반영한 소개글 전체를 출력하기\n원문에 있는 제품 사실과 가격을 유지하고, 없는 효능·인증·수치를 만들지 않기\n앞 단계에서 반영한 장점을 가능한 한 유지하기\n수정 이유나 평가 대신 수정된 본문만 Markdown으로 출력하기\n결과는 제목과 짧은 본문으로, 약 200~300자 이내로 제한하기\n\n친근하고 기억하기 쉬운 표현으로 수정한다.','GEMINI_2_5_PRO','TEXT',NULL,0.013500,'2026-09-27 19:47:28.635247','2026-09-27 19:47:28.635247'),('daa1d519-8f27-41f9-a80c-06ea8b20c9c5',2,'대중 공략 전략','LLM','당신은 브랜드 전략가다. 제품 분석 결과를 받아 전략 초안을 작성한다.\n\n[입력 위치]\n아래 「입력」 절에 제품 분석 결과가 있다. 그 안의 market_brief 가 시장 조사 요약이다\n(경쟁 강도 판정 · 경쟁사 · 트렌드). 브랜드 기획 요청서는 「사용자 코멘트」 절에 있다.\n이 셋을 함께 보고 대중 공략 전략 초안을 작성한다.\n\n[전제 — 바꾸지 않는다]\n이 요청은 경쟁이 적은 카테고리로 판정되어 배정됐다.\n따라서 approach 는 반드시 \"대중 공략\" 이다. 내용을 보고 다시 판단하지 않는다.\n\n[대중 공략 전략이란 무엇인가]\n경쟁자가 적은 시장에서 진짜 상대는 다른 브랜드가 아니라 \"없어도 그만\"이라는 무관심이다.\n- 우선 이 물건이 왜 필요한지부터 납득시킨다. 브랜드 비교는 그다음이다.\n- 고객층을 좁히지 않는다. 카테고리를 쓰는 사람 자체를 늘리는 그림을 그린다.\n- 표현을 쉽게 쓴다. 이 카테고리를 처음 접하는 사람이 읽는다고 생각한다.\n- 먼저 자리 잡는 것이 목표다. 시장이 커졌을 때 기준으로 떠오를 이름이 되게 한다.\n\n[각 항목 작성 지침]\n- approach: \"대중 공략\" 으로 고정한다.\n\n- positioning: 한 문장. \"이 카테고리 하면 떠오르는 브랜드\"가 되는 자리를 잡는다.\n  좁은 틈새가 아니라 카테고리를 대표하는 자리로 쓴다.\n\n- rationale_md: 왜 이 자리인지 3~5문장. 마크다운 허용.\n  반드시 두 가지를 근거로 든다.\n    (1) market_brief 에서 — 왜 지금 이 카테고리가 알려질 시점인지 (트렌드를 근거로)\n    (2) 제품 분석에서 — 우리 강점 중 무엇이 카테고리를 대표할 만한지\n  제품 분석의 약점 중 처음 사는 사람이 가장 걸릴 것 하나를 골라\n  어떻게 덜어 줄지 한 문장으로 적는다.\n\n- core_strategies: 3~5개. label 은 5~15자 명사구, detail 은 실행 단위로 한 문장.\n  적어도 하나는 \"이 제품이 왜 필요한지 알리는\" 전략이어야 한다.\n  \"브랜드 인지도를 높인다\" 처럼 누구에게나 해당하는 문장은 쓰지 않는다.\n\n[하지 않을 것]\n- 숫자를 지어내지 않는다. 받은 입력에 없는 수치는 쓰지 않는다.\n- 없는 경쟁사를 만들어 내지 않는다. 경쟁이 적다는 것이 이 경로의 전제다.\n- 채널·예산·일정은 여기서 정하지 않는다.\n\n[참고 — core_strategies 를 쓰는 방식]\n\n이렇게 쓰지 않는다 (누구에게나 해당하는 말):\n  · label: 브랜드 인지도 제고    detail: SNS 를 활용해 인지도를 높인다.\n  · label: 시장 선점             detail: 빠르게 시장을 선점한다.\n\n이렇게 쓴다 (읽으면 그림이 그려지는 말):\n  · label: 위험을 눈으로 보여주기\n    detail: 어두운 길에서 보이는 거리를 비교해 보여, 없을 때 무슨 일이 생기는지 알린다.\n  · label: 처음 사는 사람 기준으로 고르기\n    detail: 크기·무게 선택을 견종 한 가지만 고르면 끝나게 줄인다.\n\n첫 번째가 \"왜 필요한지 알리는\" 전략이다. 이런 항목이 적어도 하나는 있어야 한다.','GEMINI_2_5_PRO','JSON','{\"approach\":\"대중 공략\",\"positioning\":\"샘플 포지셔닝\",\"rationale_md\":\"샘플 근거\",\"core_strategies\":[{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"}]}',0.013500,'2026-09-28 00:34:53.658201','2026-09-28 00:34:53.658201'),('f0fed48f-146a-4263-9f67-c46539e864cb',1,'QA371 코드 검토','LLM','입력된 내용을 간단히 요약하세요. QA371 코드 검토 마켓 검증 전용 노드입니다.','GEMINI_2_5_FLASH','TEXT',NULL,0.013500,'2026-09-27 08:17:44.266976','2026-09-27 08:17:44.266976'),('f71d5ea8-f55b-4b0b-9bc7-01153cb8410c',4,'제품 분석','LLM','당신은 소비재 제품 기획자다. 브랜드 기획 요청서를 받아 제품 분석 결과를 작성한다.\n\n[입력 위치]\n브랜드 기획 요청서는 아래 「사용자 코멘트」 절에 있다. 그 요청서로 제품 분석 결과를 작성한다.\n앞 단계의 시장 조사 결과는 아래 「입력」 절에 있다. 그것은 market_brief 로 옮겨 적기만 하고,\nstrengths·weaknesses·opportunities 를 쓸 때는 보지 않는다.\n\n[이 분석의 범위]\n- 요청서에 적힌 제품 하나만 본다. 시장 상황·경쟁사·업계 트렌드는 다루지 않는다.\n- \"경쟁사 대비\", \"업계 평균보다\" 같은 비교 표현을 쓰지 않는다.\n- 숫자를 지어내지 않는다. 요청서에 있는 수치만 쓴다.\n\n[각 항목 작성 지침]\n- market_brief: 「입력」 절 시장 조사 결과에서 경쟁 강도 판정(competition_level)과 그 근거,\n  경쟁사 이름, 주요 트렌드를 3~4문장으로 옮겨 적는다. 해석하거나 덧붙이지 않는다.\n\n- strengths: 제품이 실제로 잘하는 것 2~5개.\n  label 은 5~15자 명사구, detail 은 한 문장.\n  요청서의 \'차별점\'을 그대로 옮겨 적지 말고, 그것이 고객에게 무엇을 해 주는지로 바꿔 쓴다.\n\n- weaknesses: 2~5개. 고객이 구매를 망설일 이유를 쓴다.\n  우리 사정(원가·생산 난이도)이 아니라 고객이 느낄 불편·불안·부담으로 쓴다.\n  좋게 들리게 다듬지 않는다. \"장점의 이면\" 형태로 쓰면 약점이 아니다.\n  예를 들어 가격이 높다면 \"왜 비싼지 설명이 필요하다\"가 아니라\n  \"같은 쓰임의 더 싼 제품이 있는데 굳이 이걸 살 이유를 모른다\"로 쓴다.\n\n- opportunities: 2~5개. 약점을 덮거나 강점을 키울 여지.\n  label 만 있는 한 줄짜리 항목이다. 짧은 명사구로 쓴다.\n\n[요청서의 \'추가 요청\']\n하지 말라고 적힌 것은 여기서도 지킨다. 표현 방식에 대한 요청이면 문장에 반영한다.\n\n[참고 — weaknesses 를 쓰는 방식]\n\n이렇게 쓰지 않는다 (약점처럼 생긴 칭찬):\n  · \"친환경 소재를 써서 원가가 다소 높습니다\"\n  · \"기능이 많아 처음에는 익숙해지는 시간이 필요합니다\"\n\n이렇게 쓴다 (고객이 망설이는 지점):\n  · label: 가격 대비 체감 어려움\n    detail: 같은 값에 대용량 제품을 살 수 있는데, 차이를 써 보기 전에는 알 수 없다.\n  · label: 교체 시점을 놓침\n    detail: 언제 갈아야 하는지 눈으로 표시되지 않아 계속 쓰게 된다.','GEMINI_2_5_PRO','JSON','{\"market_brief\":\"샘플 시장 요약\",\"strengths\":[{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"}],\"weaknesses\":[{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"},{\"label\":\"샘플 항목\",\"detail\":\"샘플 설명\"}],\"opportunities\":[{\"label\":\"샘플 항목\"},{\"label\":\"샘플 항목\"}]}',0.013500,'2026-09-27 16:43:18.892250','2026-09-27 16:43:18.892250');
/*!40000 ALTER TABLE `node_definition` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `node_ref_file`
--

DROP TABLE IF EXISTS `node_ref_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `node_ref_file` (
  `id` varchar(36) NOT NULL,
  `node_definition_id` varchar(36) NOT NULL,
  `ordinal` int NOT NULL,
  `file_key` varchar(255) NOT NULL,
  `filename` varchar(255) NOT NULL,
  `file_size` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_node_ref_file_order` (`node_definition_id`,`ordinal`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `node_ref_file`
--

LOCK TABLES `node_ref_file` WRITE;
/*!40000 ALTER TABLE `node_ref_file` DISABLE KEYS */;
INSERT INTO `node_ref_file` VALUES ('57a8a9b3-aba1-4fbb-a9dc-f165753c4973','49e36604-0795-4217-a3a9-40e67d587736',0,'nodes/49e36604-0795-4217-a3a9-40e67d587736/57a8a9b3-aba1-4fbb-a9dc-f165753c4973.pdf','보고서_작성가이드.pdf',50250,'2026-09-27 16:43:19.464891'),('ad7d7832-97bf-4b75-9560-0672d35da937','a1c119d4-0967-481c-8262-fbd89e4b80dd',0,'nodes/a1c119d4-0967-481c-8262-fbd89e4b80dd/ad7d7832-97bf-4b75-9560-0672d35da937.txt','광고카피_금지표현.txt',1192,'2026-09-27 19:22:39.977096'),('b7ee098b-3534-4948-a83e-53af1d04570f','5b7d478c-7ffd-4fb4-9344-6d769e7eccf1',0,'nodes/5b7d478c-7ffd-4fb4-9344-6d769e7eccf1/b7ee098b-3534-4948-a83e-53af1d04570f.md','시장조사_체크리스트.md',1619,'2026-09-27 16:43:19.220813');
/*!40000 ALTER TABLE `node_ref_file` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `node_test_run`
--

DROP TABLE IF EXISTS `node_test_run`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `node_test_run` (
  `id` varchar(36) NOT NULL,
  `node_id` varchar(36) NOT NULL,
  `requester_id` bigint NOT NULL,
  `run_id` varchar(64) DEFAULT NULL,
  `mock` tinyint(1) NOT NULL,
  `status` varchar(20) NOT NULL,
  `cost_usd` decimal(20,6) DEFAULT NULL,
  `failure_code` varchar(64) DEFAULT NULL,
  `result_json` text,
  `requested_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_node_test_run_node_requested` (`node_id`,`requested_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `node_test_run`
--

LOCK TABLES `node_test_run` WRITE;
/*!40000 ALTER TABLE `node_test_run` DISABLE KEYS */;
/*!40000 ALTER TABLE `node_test_run` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `wallet_auth_message`
--

DROP TABLE IF EXISTS `wallet_auth_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wallet_auth_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `wallet_address` varchar(42) NOT NULL,
  `nonce` varchar(64) NOT NULL,
  `message` text NOT NULL,
  `issued_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_wallet_auth_message_wallet_address` (`wallet_address`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wallet_auth_message`
--

LOCK TABLES `wallet_auth_message` WRITE;
/*!40000 ALTER TABLE `wallet_auth_message` DISABLE KEYS */;
INSERT INTO `wallet_auth_message` VALUES (1,'0x29893a7b0cc2b6a7ff64c3adaa93d161cc812db6','c5efe4248b3f5251e0b6d0dbe4d4aa2e865c7779bcf53782712d1d231ebce341','https://j15a403.p.ssafy.io wants you to sign in with your Ethereum account:\n0x29893a7B0Cc2B6a7ff64c3AdaA93d161cC812dB6\n\nSign in to ABC.\n\nURI: https://j15a403.p.ssafy.io/\nVersion: 1\nChain ID: 31221\nNonce: c5efe4248b3f5251e0b6d0dbe4d4aa2e865c7779bcf53782712d1d231ebce341\nIssued At: 2026-09-27T23:50:00.988266Z\nExpiration Time: 2026-09-27T23:55:00.988266Z','2026-09-27 23:50:00.988266','2026-09-27 23:55:00.988266','2026-09-27 23:50:04.229935',15,'2026-09-27 07:58:17.883159','2026-09-27 23:50:04.229935'),(2,'0x2e1a8f2741c16167796870dfe674ff437c188060','0e7a0504790df339b80b87f19c1508eb10d74f42a5100cf69a3677aa7bd8317e','https://j15a403.p.ssafy.io wants you to sign in with your Ethereum account:\n0x2E1A8f2741c16167796870DFe674Ff437C188060\n\nSign in to ABC.\n\nURI: https://j15a403.p.ssafy.io/\nVersion: 1\nChain ID: 31221\nNonce: 0e7a0504790df339b80b87f19c1508eb10d74f42a5100cf69a3677aa7bd8317e\nIssued At: 2026-09-28T01:43:34.118539Z\nExpiration Time: 2026-09-28T01:48:34.118539Z','2026-09-28 01:43:34.118539','2026-09-28 01:48:34.118539','2026-09-28 01:43:35.997431',15,'2026-09-27 15:08:45.892259','2026-09-28 01:43:35.997431'),(3,'0x535abafa3b650ab9fd7b2cd98c7ad06b18d37a3b','fd2d12e34f3c833f5eca71a13c81e38840435bcf844594aff54cacb1864ee4ea','https://j15a403.p.ssafy.io wants you to sign in with your Ethereum account:\n0x535ABAFa3B650AB9fD7b2cD98C7Ad06b18d37a3b\n\nSign in to ABC.\n\nURI: https://j15a403.p.ssafy.io/\nVersion: 1\nChain ID: 31221\nNonce: fd2d12e34f3c833f5eca71a13c81e38840435bcf844594aff54cacb1864ee4ea\nIssued At: 2026-09-28T00:10:00.541882Z\nExpiration Time: 2026-09-28T00:15:00.541882Z','2026-09-28 00:10:00.541882','2026-09-28 00:15:00.541882','2026-09-28 00:10:03.882267',5,'2026-09-27 15:15:24.252217','2026-09-28 00:10:03.882267'),(4,'0xde75a3c96fae7be1132f8cddff30c10ea4204e58','19f98127438f401b693583228d62ead7e9b1a74039adb66e3a41537132bd3cfd','https://j15a403.p.ssafy.io wants you to sign in with your Ethereum account:\n0xdE75a3c96faE7BE1132f8CDdFF30c10ea4204E58\n\nSign in to ABC.\n\nURI: https://j15a403.p.ssafy.io/\nVersion: 1\nChain ID: 31221\nNonce: 19f98127438f401b693583228d62ead7e9b1a74039adb66e3a41537132bd3cfd\nIssued At: 2026-09-28T01:16:01.300551Z\nExpiration Time: 2026-09-28T01:21:01.300551Z','2026-09-28 01:16:01.300551','2026-09-28 01:21:01.300551','2026-09-28 01:16:02.932527',9,'2026-09-27 15:41:04.287652','2026-09-28 01:16:02.932527'),(5,'0x3789c8697da6b4bd02bcea25e53569433cdab301','2b3a5005a305b983265f07231411562495ecfa60a504e8a5bef80a94b4f6e9d6','https://j15a403.p.ssafy.io wants you to sign in with your Ethereum account:\n0x3789C8697DA6b4bd02bCEA25e53569433cDAB301\n\nSign in to ABC.\n\nURI: https://j15a403.p.ssafy.io/\nVersion: 1\nChain ID: 31221\nNonce: 2b3a5005a305b983265f07231411562495ecfa60a504e8a5bef80a94b4f6e9d6\nIssued At: 2026-09-27T20:50:06.716842Z\nExpiration Time: 2026-09-27T20:55:06.716842Z','2026-09-27 20:50:06.716842','2026-09-27 20:55:06.716842','2026-09-27 20:50:08.677990',1,'2026-09-27 20:50:06.718504','2026-09-27 20:50:08.677990');
/*!40000 ALTER TABLE `wallet_auth_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `withdrawal`
--

DROP TABLE IF EXISTS `withdrawal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `withdrawal` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` varchar(36) NOT NULL,
  `requester_id` bigint NOT NULL,
  `amount_abc` decimal(41,6) NOT NULL,
  `wallet_address` varchar(42) NOT NULL,
  `status` varchar(20) NOT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reject_reason` varchar(200) DEFAULT NULL,
  `payout_transaction_hash` varchar(66) DEFAULT NULL,
  `payout_raw_transaction` text,
  `payout_submitted_at` datetime(6) DEFAULT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_withdrawal_requester_request` (`requester_id`,`request_id`),
  UNIQUE KEY `uq_withdrawal_payout_transaction_hash` (`payout_transaction_hash`),
  KEY `idx_withdrawal_requester_status` (`requester_id`,`status`),
  KEY `idx_withdrawal_status_created` (`status`,`created_at`),
  CONSTRAINT `ck_withdrawal_amount_positive` CHECK ((`amount_abc` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `withdrawal`
--

LOCK TABLES `withdrawal` WRITE;
/*!40000 ALTER TABLE `withdrawal` DISABLE KEYS */;
/*!40000 ALTER TABLE `withdrawal` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `workflow`
--

DROP TABLE IF EXISTS `workflow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `workflow` (
  `id` varchar(36) NOT NULL,
  `owner_id` bigint NOT NULL,
  `graph_id` varchar(36) NOT NULL,
  `name` varchar(40) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `validation_status` varchar(20) NOT NULL,
  `revision` bigint NOT NULL,
  `last_executed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_workflow_owner_last_executed` (`owner_id`,`last_executed_at`,`created_at`,`id`),
  KEY `idx_workflow_owner_created` (`owner_id`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `workflow`
--

LOCK TABLES `workflow` WRITE;
/*!40000 ALTER TABLE `workflow` DISABLE KEYS */;
INSERT INTO `workflow` VALUES ('16361db4-2179-41b9-9821-d7034f997e66',1,'a26da5a2-5a12-452e-9398-4d40baf4aec4','부자가되자',NULL,'NOT_VALIDATED',1,NULL,'2026-09-27 21:04:52.945623','2026-09-27 21:04:52.945623'),('30078b76-e5cf-4709-a37d-f0ca95e5264e',4,'5b801053-d621-488b-b40c-55b8f1c06621','제품 강점 에디터',NULL,'NOT_VALIDATED',1,NULL,'2026-09-27 19:37:09.673861','2026-09-27 19:37:09.673861'),('3486d1bb-79ff-40c0-a9bc-9dd8a906cef9',4,'7250fad1-35c6-4ce6-aa61-680fc629532a','고객 공감 에디터',NULL,'NOT_VALIDATED',2,NULL,'2026-09-27 19:44:54.179655','2026-09-27 19:44:55.857295'),('47485ac2-f022-45bc-a604-60b3064dc8f4',3,'25084168-82b2-4efd-8bcd-97e567c666f1','hs',NULL,'NOT_VALIDATED',1,NULL,'2026-09-27 19:53:46.348469','2026-09-27 19:53:46.348469'),('4aa70c6b-7878-4ae1-9ae5-fc3ee3befe3b',4,'c225171c-a9a5-4f1c-bba6-2cee3b4c3289','광고 카피 생성기',NULL,'NOT_VALIDATED',2,NULL,'2026-09-27 19:22:39.912810','2026-09-27 19:22:43.359055'),('4d04999f-0741-426d-8ffb-0cdb14b51f2f',4,'d36290ed-d794-4a0d-8023-6d57b9823e99','홍보글 작성',NULL,'NOT_VALIDATED',1,NULL,'2026-09-28 01:46:58.247114','2026-09-28 01:46:58.247114'),('5052cee1-9e23-4be4-97e8-f76afdac468f',4,'13562329-fdcd-4628-b3fa-74659c5aeee7','브랜드 말투 에디터',NULL,'NOT_VALIDATED',2,NULL,'2026-09-27 19:47:28.641945','2026-09-27 19:47:29.602747'),('55660a62-31f9-43b3-8656-14e8ff917ac4',1,'9d38f62b-a4c5-48fc-a053-1e830005b66c','QA371-06 회의 정리 테스트',NULL,'NOT_VALIDATED',1,NULL,'2026-09-27 08:17:52.068171','2026-09-27 08:17:52.068171'),('57f76550-6926-4ad4-80c0-b98e2401e1da',1,'f5bbcdaa-ab09-4ac3-92f3-1b30df9b80ec','QA371-02 매출 점검 테스트',NULL,'NOT_VALIDATED',1,NULL,'2026-09-27 08:15:06.730701','2026-09-27 08:15:06.730701'),('59351162-9c9f-42a0-b650-651c349beaa9',1,'4ee6ab67-3b68-4c89-b8ce-582ab5ccd097','QA371-09 리뷰 요약 테스트',NULL,'NOT_VALIDATED',1,NULL,'2026-09-27 08:18:33.515513','2026-09-27 08:18:33.515513'),('698e9fce-fec3-4986-a9ea-91fdc8fbc510',1,'7fcf88e6-1b94-4c25-9c80-79f29d03ca19','QA371-03 문의 답변 테스트',NULL,'NOT_VALIDATED',1,NULL,'2026-09-27 08:17:13.167563','2026-09-27 08:17:13.167563'),('73ce6967-0178-487b-883b-8d7ad3038ac3',1,'a06a1444-56a3-4f88-8864-8145a2794bfe','QA371-10 계약 비교 테스트',NULL,'NOT_VALIDATED',1,NULL,'2026-09-27 08:18:43.104928','2026-09-27 08:18:43.104928'),('764423c5-deba-459f-890f-04fc6e47bb72',1,'74bd5257-a9e4-4d30-850c-42fac85e5640','QA371-07 문서 분류 테스트',NULL,'NOT_VALIDATED',1,NULL,'2026-09-27 08:17:56.548204','2026-09-27 08:17:56.548204'),('7d44c8df-2b17-4c37-8328-5af1928cd206',4,'9930a62c-f22a-434b-a7ee-67e2d16f9b19','zaa',NULL,'NOT_VALIDATED',1,NULL,'2026-09-28 00:57:17.704119','2026-09-28 00:57:17.704119'),('7da5d2c8-8ea3-4059-85a6-d1e14dc430ae',1,'c1e33fa7-4475-4cb5-876d-0042395d0685','QA371-04 일정 정리 테스트',NULL,'NOT_VALIDATED',1,NULL,'2026-09-27 08:17:39.000472','2026-09-27 08:17:39.000472'),('7dacdae5-f6d1-4500-9a12-fba8076dbefa',1,'0cefc92d-98ec-47fa-a0d9-550d031c43b1','QA371-08 이메일 초안 테스트',NULL,'NOT_VALIDATED',1,NULL,'2026-09-27 08:18:29.708965','2026-09-27 08:18:29.708965'),('7e4a8f5e-06c0-4b63-897d-05e4b83aabb5',4,'2f26713b-a934-42cd-93dc-973a15084021','SNS 캠페인 기획기',NULL,'NOT_VALIDATED',2,NULL,'2026-09-27 19:15:32.168531','2026-09-27 19:15:33.806215'),('7ed1080a-e487-4609-8289-1c85eab55ba2',1,'252cbd11-ade7-44e2-823f-40851b1f25ba','QA371-05 코드 검토 테스트',NULL,'NOT_VALIDATED',1,NULL,'2026-09-27 08:17:44.270690','2026-09-27 08:17:44.270690'),('8795d8c3-c1fc-4814-9c99-9f07ed2bd3d7',2,'e4e343b4-f151-4f2d-8a1c-0aa019cb6a10','제품 분석',NULL,'NOT_VALIDATED',2,NULL,'2026-09-28 00:20:42.972851','2026-09-28 00:20:44.590266'),('99e0fd45-b342-4697-b4e1-bc91dd70751b',4,'5c036ec2-efb3-4d21-b7e5-9828730e544a','.',NULL,'NOT_VALIDATED',3,NULL,'2026-09-27 16:57:22.379021','2026-09-28 00:14:24.935696'),('becef94d-a89b-4001-8353-3568bccc4a1f',2,'00b499b9-25e8-481a-8fd3-68bca08b0f44','홍보글 작성',NULL,'NOT_VALIDATED',5,NULL,'2026-09-28 01:23:48.168087','2026-09-28 01:35:31.502676'),('d2afd46d-0c93-47ea-9d30-17172bd0d913',2,'a0adf15c-a862-40b7-977c-bcdca51b2dc6','대중 공략 전략기',NULL,'NOT_VALIDATED',1,NULL,'2026-09-28 00:34:53.665116','2026-09-28 00:34:53.665116'),('f8823ef3-7c8e-4e86-8439-2150ef00b95e',1,'f3b571be-e276-4436-a10e-ea7b4676e6d2','QA371-01 계약 요약 테스트',NULL,'NOT_VALIDATED',1,NULL,'2026-09-27 08:10:45.495437','2026-09-27 08:10:45.495437'),('ff4893e6-25bf-477a-ba8b-c7770ada3b1b',4,'ff41fe27-9be4-45f9-9ecb-8d0907ff57ae','칫솔 사업 가보자고',NULL,'NOT_VALIDATED',5,NULL,'2026-09-27 16:43:19.018831','2026-09-28 00:37:30.753865');
/*!40000 ALTER TABLE `workflow` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping events for database 'ai-block-clock'
--

--
-- Dumping routines for database 'ai-block-clock'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-28  2:32:57
