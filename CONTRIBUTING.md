# CONTRIBUTING

Obrigado pelo interesse em contribuir com o `snk-slack`.

## Como contribuir

1. Fork o repositorio.
2. Crie uma branch: `git checkout -b feat/minha-melhoria`.
3. Faca as mudancas e rode `./test.sh` localmente.
4. Commit com mensagem clara (imperativo, em portugues ou ingles).
5. Push e abra um pull request contra `main`.

## Regras de codigo Java

- Pacote: sempre `br.com.lbi.slack`.
- HTTP: apenas `java.net.HttpURLConnection`. **Nao adicionar** OkHttp, Apache HttpClient, Retrofit, etc.
- JSON: apenas `com.google.gson.*` (Gson 2.x — compativel com todo Sankhya tipico).
- Compat: manter API publica estavel. Mudancas que quebrem assinatura de metodos publicos exigem bump de versao major.
- Java 8+: usar apenas features compativeis com Java 8 (nem mesmo `var`).

## Regras de Markdown

- Sem emojis.
- Sem acentos em codigo/exemplos (copy-paste tende a corromper).
- Headings consistentes (um `#` por arquivo, subsecoes com `##`).
- Passa markdownlint-cli2 (ver `.markdownlint.json`).

## Issues

Antes de abrir, procure duplicatas. Inclua:

- Versao Java / Sankhya W / banco.
- Stack trace completo (se aplicavel).
- Valor da preferencia `LOGSLACK_WEBHOOK` (**redigido** — nao cole a URL real).
- Passos pra reproduzir.

## Seguranca

Encontrou vulnerabilidade? Nao abra issue publico. E-mail direto: `lbigor@icloud.com`.

## Licenca

Contribuicoes ficam sob MIT (mesma do projeto).
