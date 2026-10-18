# Olink

Aplicativo Android para pesquisar vídeos do YouTube por artista ou nome de música, visualizar título/canal/miniatura e copiar ou abrir o link do vídeo.

## Recursos
- Busca por artista ou música.
- Até 20 resultados por página e opção de carregar mais.
- Miniaturas, título e nome do canal.
- Copiar link e abrir no YouTube.
- Chave da API configurada dentro do aplicativo e salva localmente no aparelho.
- Sem anúncios e sem dependências externas de bibliotecas.

## Requisito da API
O aplicativo usa a API oficial **YouTube Data API v3**. É necessário ter um projeto no Google Cloud, ativar essa API e criar uma chave.

1. Abra https://console.cloud.google.com/apis/library/youtube.googleapis.com
2. Selecione ou crie um projeto no Google Cloud.
3. Ative YouTube Data API v3.
4. Crie uma chave em APIs e serviços > Credenciais.
5. Instale o APK e toque em **Configurar API** para colar a chave.

A chave não está incluída no código deste repositório. Configure restrições e acompanhe as cotas no Google Cloud. Como a chave é usada por um aplicativo cliente, não a considere um segredo inviolável; restrinja-a à API necessária e monitore o uso.

## Compilar
O workflow GitHub Actions compila o APK de depuração em cada push para `main` ou `master`, e também pode ser iniciado manualmente em Actions > Build APK > Run workflow.

O artefato é publicado com o nome `Olink-debug`.

## Pacote
`com.oliveira.olink`

## Nota
Olink pesquisa e organiza links públicos do YouTube; não baixa nem hospeda vídeos.
