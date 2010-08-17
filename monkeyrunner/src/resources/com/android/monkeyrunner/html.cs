<html>
<body>
<h1>MonkeyRunner Help<h1>
<h2>Table of Contents</h2>
<ul>
<?cs each:item = help ?>
<li><a href="#<?cs name:item ?>"><?cs var:item.name ?></a></li>
<?cs /each ?>
</ul>
<?cs each:item = help ?>
<h2><a name="<?cs name:item ?>"><?cs var:item.name ?></a></h2>
  <?cs each:docpara = item.doc ?>
  <p><?cs var:docpara ?></p>
  <?cs /each ?>
    <?cs if:subcount(item.argument) ?>
<h3>Args</h3>
<ul>
      <?cs each:arg = item.argument ?>
        <li><?cs var:arg.name ?> - <?cs each:argdocpara = arg.doc ?><?cs var:argdocpara ?> <?cs /each ?>
      <?cs /each ?>
</ul>
<h3>Returns</h3>
<p><?cs each:retdocpara = item.returns ?><?cs var:retdocpara ?> <?cs /each ?></p>
<?cs /if ?>
<?cs /each ?>
</body>
</html>
