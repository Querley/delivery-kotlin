# Delivery Kotlin Console

Atividade 04 de Desenvolvimento de Sistemas para Dispositivos Móveis.

Sistema de delivery com duas aplicações de console: uma para o restaurante e outra para o cliente. As duas usam a mesma pasta de arquivos para compartilhar os pedidos.

[Baixar a versão compilada](https://github.com/Querley/delivery-kotlin/releases/latest/download/delivery-kotlin.zip)

Para usar o ZIP, extraia o conteúdo, entre na pasta `delivery-kotlin` e execute `bin/restaurante.bat` e `bin/cliente.bat` em dois terminais abertos nessa pasta. As bibliotecas já estão incluídas; ainda é necessário ter Java 17 ou superior instalado. Os comandos abaixo são para quem preferir compilar o código.

## Como executar

É necessário ter o JDK 17 ou 21 instalado. O Gradle Wrapper está incluído no projeto.

Na pasta do projeto, compile pelo PowerShell:

```powershell
.\gradlew.bat installDist
```

Abra dois terminais na pasta do projeto. No primeiro, execute o restaurante:

```powershell
.\build\install\delivery-kotlin\bin\restaurante.bat --dados .\dados
```

No segundo, execute o cliente:

```powershell
.\build\install\delivery-kotlin\bin\cliente.bat --dados .\dados
```

No Linux ou macOS, use `./gradlew installDist` e os arquivos `restaurante` e `cliente`, sem `.bat`, na mesma pasta `bin`.

Os dois programas precisam apontar para a **mesma pasta de dados**. Se forem abertos em pastas diferentes, passe o mesmo caminho absoluto em `--dados`. Sem esse argumento, é usada a subpasta `dados` do diretório atual. O caminho completo aparece ao iniciar. A pasta é criada automaticamente.

## Restaurante

- Cadastro com nome, e-mail único, endereço e cardápio inicial.
- Acesso pelo e-mail cadastrado.
- Consulta, adição e remoção de itens do cardápio.
- Consulta de pedidos por status, somente do restaurante logado.
- Alteração do status de todas as linhas de um pedido pelo ID.

No cadastro do cardápio, pressione **Enter** no número do item para terminar. O número identifica o item dentro do restaurante e não pode se repetir no mesmo cardápio.

## Cliente

- Cadastro com nome, telefone único e endereço.
- Acesso pelo telefone cadastrado.
- Escolha de restaurante e seleção de itens e quantidades.
- Resumo com total e confirmação por **S/N**.
- Consulta dos próprios pedidos em andamento e finalizados.

Na seleção do pedido, pressione **Enter** no número do item para ver o resumo. Repetir um item soma as quantidades. Um pedido vazio ou cancelado não é gravado.

## Arquivos

- `restaurante_ID.json`: um arquivo por restaurante, com nome, e-mail, endereço e menu.
- `clientes.json`: lista de clientes.
- `pedidos.csv`: uma linha por item do pedido, separada por ponto e vírgula.

Os arquivos são criados quando os primeiros cadastros e pedidos são confirmados. O cabeçalho do CSV segue o enunciado:

```text
id_pedido;data_hora;email_restaurante;nome_restaurante;telefone_cliente;nome_cliente;endereco_cliente;numero_item;quantidade;descricao_item;valor_unitario;valor_total_item;status
```

| Código | Status |
| --- | --- |
| 0 | SOLICITADO |
| 1 | EM PREPARACAO |
| 2 | AGUARDANDO ENTREGADOR |
| 3 | EM TRANSITO |
| 4 | ENTREGUE |

Cada pedido tem um ID único e a data/hora local da confirmação. Seus itens compartilham o mesmo ID e status. Os preços e dados de entrega ficam registrados no pedido, mesmo que o cardápio seja alterado depois.

E-mails são comparados em minúsculas e telefones são salvos apenas com dígitos. Preços aceitam vírgula ou ponto decimal, sem separador de milhar. O sistema usa `BigDecimal` para os valores monetários. A trava `.delivery.lock` evita gravações simultâneas sobre os mesmos dados, e cada consulta relê os arquivos. Os dados de uso ficam fora do Git.

O acesso segue o enunciado, apenas por e-mail ou telefone, sem senha. É uma aplicação acadêmica local, sem servidor, pagamentos ou entregador separado.

## Exemplo de uso

1. No restaurante, escolha **Novo Cadastro** e informe os dados.
2. Cadastre o item 1, Pizza Calabresa, por 45,00 e o item 2, Refrigerante 2L, por 10,00. Pressione Enter para terminar.
3. No cliente, faça um cadastro e escolha **Realizar Novo Pedido**.
4. Selecione o restaurante, peça uma pizza e dois refrigerantes. O total será 65,00.
5. Confirme com **S** e copie o ID exibido.
6. No restaurante, consulte os pedidos e altere o status pelo ID.
7. Quando o status for 4, o cliente verá o pedido em **Ver Pedidos Finalizados**.

## Organização e testes

- `Modelos.kt`: classes de dados e validações.
- `Arquivos.kt`: leitura e gravação de JSON e CSV.
- `Delivery.kt`: operações de cadastro, cardápio e pedidos.
- `Terminal.kt`: leitura das entradas e exibição dos dados.
- `restaurante/Restaurante.kt` e `cliente/Cliente.kt`: menus das duas aplicações.

Para executar os testes:

```powershell
.\gradlew.bat test
```

As bibliotecas Gson e Apache Commons CSV cuidam dos formatos JSON e CSV. Os testes usam JUnit e pastas temporárias, sem alterar os dados de uso.

São 15 testes que cobrem cadastros únicos, cardápio, cálculo do total, os cinco status, filtros de acesso, cancelamento, entradas inválidas e preservação dos arquivos. Dois deles executam as aplicações em processos Java separados, incluindo pedidos simultâneos de dois clientes.
