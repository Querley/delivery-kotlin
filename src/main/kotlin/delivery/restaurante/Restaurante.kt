package delivery.restaurante

import delivery.*

fun main(args: Array<String>) = executarRestaurante(args, Terminal())

fun executarRestaurante(args: Array<String>, terminal: Terminal) = iniciar(args, terminal) { sistema ->
    while (true) {
        terminal.mostrar("\nAPP RESTAURANTE\n[1] Entrar como Restaurante Existente\n[2] Novo Cadastro\n[0] Sair")
        when (terminal.ler("Opção: ")) {
            "0" -> break
            "1" -> terminal.tentar {
                val restaurante = sistema.entrarRestaurante(terminal.texto("E-mail: "))
                if (restaurante == null) terminal.mostrar("Restaurante não encontrado.")
                else menuRestaurante(sistema, terminal, restaurante.email)
            }
            "2" -> terminal.tentar {
                val nome = terminal.texto("Nome: ")
                val email = normalizarEmail(terminal.texto("E-mail: "))
                require(sistema.entrarRestaurante(email) == null) { "E-mail já cadastrado." }
                val endereco = terminal.texto("Endereço: ")
                val itens = mutableListOf<ItemCardapio>()
                terminal.mostrar("Cadastre o cardápio inicial. Pressione Enter no número para terminar.")
                while (true) {
                    val numero = terminal.numeroOuFim("Número do item: ") ?: break
                    if (itens.any { it.numeroItem == numero }) {
                        terminal.mostrar("Número já utilizado no cardápio.")
                        continue
                    }
                    itens.add(ItemCardapio(numero, terminal.texto("Descrição: "), terminal.preco()))
                }
                val restaurante = sistema.cadastrarRestaurante(nome, email, endereco, itens)
                terminal.mostrar("Restaurante cadastrado com sucesso.")
                menuRestaurante(sistema, terminal, restaurante.email)
            }
            else -> terminal.mostrar("Opção inválida.")
        }
    }
}

private fun menuRestaurante(sistema: Delivery, terminal: Terminal, email: String) {
    while (true) {
        terminal.mostrar("\nRESTAURANTE: $email\n[1] Gerenciar Cardápio\n[2] Visualizar Pedidos por Status\n[3] Alterar Status do Pedido\n[0] Sair da conta")
        when (terminal.ler("Opção: ")) {
            "0" -> return
            "1" -> gerenciarCardapio(sistema, terminal, email)
            "2" -> terminal.tentar {
                STATUS.forEachIndexed { codigo, texto -> terminal.mostrar("$codigo - $texto") }
                val entrada = terminal.ler("Status (Enter para todos): ")
                val status = if (entrada.isEmpty()) null else entrada.toIntOrNull().also {
                    require(it != null && it in 0..4) { "Informe um status entre 0 e 4." }
                }
                terminal.pedidos(sistema.pedidosRestaurante(email, status))
            }
            "3" -> terminal.tentar {
                val id = terminal.texto("ID do pedido: ")
                STATUS.forEachIndexed { codigo, texto -> terminal.mostrar("$codigo - $texto") }
                val status = terminal.inteiro("Novo status: ", 0..4)
                sistema.alterarStatus(email, id, status)
                terminal.mostrar("Status atualizado.")
            }
            else -> terminal.mostrar("Opção inválida.")
        }
    }
}

private fun gerenciarCardapio(sistema: Delivery, terminal: Terminal, email: String) {
    while (true) {
        terminal.mostrar("\n[A] Ver Cardápio\n[B] Adicionar Item\n[C] Remover Item\n[0] Voltar")
        when (terminal.ler("Opção: ").uppercase()) {
            "0" -> return
            "A" -> terminal.tentar { terminal.cardapio(requireNotNull(sistema.entrarRestaurante(email))) }
            "B" -> terminal.tentar {
                val numero = terminal.inteiro("Número do item: ")
                val descricao = terminal.texto("Descrição: ")
                sistema.adicionarItem(email, ItemCardapio(numero, descricao, terminal.preco()))
                terminal.mostrar("Item adicionado.")
            }
            "C" -> terminal.tentar {
                sistema.removerItem(email, terminal.inteiro("Número do item a remover: "))
                terminal.mostrar("Item removido.")
            }
            else -> terminal.mostrar("Opção inválida.")
        }
    }
}
