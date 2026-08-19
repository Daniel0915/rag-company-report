# ISMS-P 인증 준비 챗봇 (Spring Boot + Spring AI)

[rag-system-ismsp](https://github.com/Daniel0915/rag-system-ismsp)(TypeScript + Express + LangChain.js)에 구현한 ISMS-P 인증 준비 챗봇 기능을, Java/Spring 스택으로 옮겨본 프로젝트입니다. 기능 범위는 TS 버전의 "문서 등록 + 메타데이터 라벨링 + 등록 문서 기반 채팅"까지 동일하게 맞췄고, GAP 분석/증적 판정 등 TS 저장소에서도 아직 구현되지 않은 부분은 이 포트에도 없습니다.

## 실행 방법

사전 준비: [Ollama](https://ollama.com)가 로컬에서 실행 중이어야 합니다.

```bash
ollama serve
ollama pull qwen2.5:3b
ollama pull nomic-embed-text
```

```bash
./gradlew bootRun
```

`http://localhost:8080` 접속.

## 기술 스택 / 매핑 (TS 버전과 비교)

| TypeScript 버전 | Spring 버전 |
|---|---|
| `HNSWLib` + JSON 사이드카 (`VectorStore`) | Spring AI `SimpleVectorStore`(인메모리 + JSON 파일 저장) + 청크 ID를 추적하는 JSON 사이드카 |
| `OllamaEmbeddings`/`ChatOllama` (`@langchain/ollama`) | `spring-ai-starter-model-ollama` (자동설정된 `EmbeddingModel`/`ChatClient`) |
| `PDFLoader` (`@langchain/community`) | `PagePdfDocumentReader` (`spring-ai-pdf-document-reader`) |
| `chunkPolicyDoc.ts` (정규식 기반 장-조-항 청킹 + 폴백) | `PolicyDocChunker`(동일 로직, `TokenTextSplitter`로 폴백) |
| `similaritySearch(query, k, filter)` | `SearchRequest.builder().filterExpression(...)` (`FilterExpressionBuilder`) |
| Express 라우터 (`routes/isms-p.ts`) | `@RestController` (`IsmsPController`) |

## 겪은 문제와 해결

- **`SimpleVectorStore.delete(Filter.Expression)`이 `UnsupportedOperationException`을 던짐** — 1.0.0 기준 `SimpleVectorStore`는 ID 리스트 삭제(`delete(List<String>)`)만 실제로 구현되어 있고, 필터 기반 삭제는 상위 추상 클래스의 기본 구현(미지원)을 그대로 물려받는다. TS 버전처럼 파일명으로 필터링해서 지우는 대신, 업로드 시 생성된 청크 ID를 사이드카에 저장해뒀다가 그 ID 리스트로 삭제하도록 변경.
- **`SimpleVectorStore.save(File)`이 부모 디렉터리가 없으면 `NoSuchFileException`을 던짐** — 서비스 생성자에서 사이드카/벡터스토어 파일의 부모 디렉터리를 미리 생성하도록 수정.

## 구조

```
src/main/java/com/ismsp/chatbot/
  ChatbotApplication.java        # 엔트리포인트
  controller/IsmsPController.java # REST 엔드포인트 (upload/chat/delete-all/metadata-options)
  service/
    CompanyDocIndexService.java  # 문서 등록 + 해시 기반 업서트 + 벡터스토어 반영
    ChatService.java             # 메타데이터 필터 기반 채팅 (ChatClient + SimpleVectorStore)
  chunking/PolicyDocChunker.java  # 장-조-항 청킹 (조문 밀도 검사 후 폴백)
  config/VectorStoreConfig.java   # SimpleVectorStore 빈 (파일에서 로드)
  dto/                            # 요청/응답 레코드

src/main/resources/
  application.yml                 # Ollama 모델/엔드포인트 설정
  static/                         # 프론트엔드 (TS 버전의 public/isms-p/ 재사용)
```

## 검증 상태

로컬 Ollama(`qwen2.5:3b` + `nomic-embed-text`)로 실제 호출 확인:
- 문서 등록(신규/스킵) → 등록 문서 기반 채팅(메타데이터 필터 적용/미적용) → 전체 삭제 전체 흐름
- 서버 재시작 후에도 벡터스토어·사이드카가 파일에서 정상 복원되어 채팅 가능한지 확인
