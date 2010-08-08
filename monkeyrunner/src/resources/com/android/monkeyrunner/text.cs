MonkeyRunner help
<?cs each:item = help ?>
<?cs var:item.name ?>
  <?cs each:docpara = item.doc ?><?cs var:docpara ?>
  <?cs /each ?>

<?cs if:subcount(item.argument) ?>  Args:<?cs each:arg = item.argument ?>
    <?cs var:arg.name ?> - <?cs each:argdocpara = arg.doc ?><?cs var:argdocpara ?> <?cs /each ?><?cs /each ?>
<?cs /if ?>  Returns: <?cs each:retdocpara = item.returns ?><?cs var:retdocpara ?> <?cs /each ?>
<?cs /each ?>
