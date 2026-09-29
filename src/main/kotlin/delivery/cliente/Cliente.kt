package delivery.cliente

import delivery.*
import java.math.BigDecimal

fun main(args: Array<String>) = executarCliente(args, Terminal())

fun executarCliente(args: Array<String>, terminal: Terminal) = iniciar(args, terminal) { sistema ->
    while (true) {
        terminal.mostrar("\nAPP CLIENTE\n[1] Entrar\n[2] Novo Cadastro\n[0] Sair")
        when (terminal.ler("Opção: ")) {
            "0" -> break
            "1" -> terminal.tentar {
                val cliente = sistema.entrarCliente(terminal.texto("Telefone: "))
                if (cliente == null) terminal.mostrar("Cliente não encontrado.")
                else menuCliente(sistema, terminal, cliente)
            }
            "2" -> terminal.tentar {
                val nome = terminal.texto("Nome: ")
                val telefone = normalizarTelefone(terminal.texto("Telefone: "))
                require(sistema.entrarCliente(telefone) == null) { "Telefone já cadastrado." }
                val endereco = terminal.texto("Endereço: ")
                val cliente = sistema.cadastrarCliente(nome, telefone, endereco)
                terminal.mostrar("Cliente cadastrado com sucesso.")
                menuCliente(sistema, terminal, cliente)
            }
            else -> terminal.mostrar("Opção inválida.")
        }
    }
}

private fun menuCliente(sistema: Delivery, terminal: Terminal, cliente: Cliente) {
    while (true) {
        terminal.mostrar("\nCLIENTE: ${cliente.nome}\n[1] Realizar Novo Pedido\n[2] Ver Pedidos em Andamento\n[3] Ver Pedidos Finalizados\n[0] Sair da conta")
        when (terminal.ler("Opção: ")) {
            "0" -> return
            "1" -> terminal.tentar { novoPedido(sistema, terminal, cliente) }
            "2" -> terminal.tentar { terminal.pedidos(sistema.pedidosCliente(cliente.telefone, false)) }
            "3" -> terminal.tentar { terminal.pedidos(sistema.pedidosCliente(cliente.telefone, true)) }
            else -> terminal.mostrar("Opção inválida.")
        }
    }
}

private fun novoPedido(sistema: Delivery, terminal: Terminal, cliente: Cliente) {
    val restaurantes = sistema.listarRestaurantes()
    if (restaurantes.isEmpty()) { terminal.mostrar("Nenhum restaurante cadastrado."); return }
    restaurantes.forEachIndexed { indice, restaurante ->
        terminal.mostrar("[${indice + 1}] ${restaurante.nome} - ${restaurante.endereco}")
    }
    val opcao = terminal.inteiro("Restaurante (0 para voltar): ", 0..restaurantes.size)
    if (opcao == 0) return
    val restaurante = restaurantes[opcao - 1]
    terminal.cardapio(restaurante)
    if (restaurante.menu.isEmpty()) return

    val itens = linkedMapOf<Int, ItemPedido>()
    terminal.mostrar("Pressione Enter no número do item para concluir a seleção.")
    while (true) {
        val numero = terminal.numeroOuFim("Número do item: ") ?: break
        val item = restaurante.menu.find { it.numeroItem == numero }
        if (item == null) { terminal.mostrar("Item não encontrado."); continue }
        val quantidade = terminal.inteiro("Quantidade: ", 1..999)
        val totalQuantidade = quantidade + (itens[numero]?.quantidade ?: 0)
        if (totalQuantidade > 999) { terminal.mostrar("Limite de 999 unidades por item."); continue }
        itens[numero] = ItemPedido(numero, totalQuantidade, item.descricao, item.preco)
    }
    if (itens.isEmpty()) { terminal.mostrar("Pedido vazio. Nada foi gravado."); return }
    terminal.mostrar("\nRESUMO DO PEDIDO - ${restaurante.nome}")
    terminal.mostrar("Cliente: ${cliente.nome}\nEntrega: ${cliente.endereco}")
    itens.values.forEach { terminal.mostrar("${it.quantidade} x ${it.descricao} | ${moeda(it.total)}") }
    val total = itens.values.fold(BigDecimal.ZERO) { soma, item -> soma + item.total }
    terminal.mostrar("Total: ${moeda(total)}")
    if (!terminal.confirmar()) { terminal.mostrar("Pedido cancelado. Nada foi gravado."); return }
    val pedido = sistema.registrarPedido(restaurante.email, cliente.telefone, itens.values.toList())
    terminal.mostrar("Pedido confirmado! ID: ${pedido.first().idPedido}")
    terminal.mostrar("Status: 0 - SOLICITADO")
}
