import Vue from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import moment from 'moment'
import page from 'v-page';

Vue.config.productionTip = false

// Include jQuery
const $ = require('jquery')
window.$ = $

// Include v-page for pagination
Vue.use(page, {
  language: 'en',
})

// Include Prettycron
var prettyCron = require('prettycron');

// Include ApexCharts
import VueApexCharts from 'vue-apexcharts'
import { event } from 'jquery'
Vue.use(VueApexCharts)
Vue.component('apexchart', VueApexCharts)

/* Resize Columns */
Vue.directive('columns-resizable', {
  inserted (el, binding) {
      if (el.nodeName !== 'TABLE') { return console.error('This directive is only valid on a table!'); }

      const opt = binding.value || {};
      const table = el;
      const thead = table.querySelector('thead');
      const ths = thead.querySelectorAll('th');
      const calcTableWidth = () => {
          let tableWidth = 0;
          let width = 0;

          ths.forEach((th) => {
              if (th.style.width) {
                  width = Number(parseInt(th.style.width));
              } else {
                  width = th.offsetWidth;
              }
              tableWidth += width;
          }, 0);

          return tableWidth;
      };
      const applyTableWidth = () => {
          if (opt.fixedWidthTable) {
              table.style.width = calcTableWidth();
              table.style.maxWidth = 'none';
          } else if (!table.style.width) {
              table.style.width = '100%';
              table.style.maxWidth = '100%';
          }
      };
      const handleResize = e => {
          if (opt.resizable === false) return;

          if (!opt.fixedWidthTable) {
              activeTh.style.width = parseInt(activeTh.style.width) + e.movementX + 'px';
              neighbourghTh.style.width = parseInt(neighbourghTh.style.width) - e.movementX + 'px';
          } else {
              activeTh.style.width = parseInt(activeTh.style.width) + e.movementX + 'px';
              table.style.width = parseInt(table.style.width) + e.movementX + 'px';
          }
      };

      let activeTh = null; // the th being resized
      let neighbourghTh = null; // the next except when the last column is being resized in that case it is the previous
      let resizing = false; // a resize started needed because we can not detect event handler was attached or not
      table.style.position = 'relative';

      applyTableWidth();

      ths.forEach((th, index) => {
          // initilise the width if th does not already have it
          if (!th.style.width) {
              th.style.width = th.offsetWidth + 'px';
          }

          th.originalWidth = th.style.width;
          const bar = document.createElement('div');

          bar.style.position = 'absolute';
          bar.style.right = 0;
          bar.style.top = 0;
          bar.style.bottom = 0;
          bar.style.cursor = 'col-resize';

          // customisable options
          bar.style.width = opt.handleWidth || '8px';
          bar.style.zIndex = opt.zIndex || 1;
          bar.className = opt.handleClass || 'columns-resize-bar';

          bar.addEventListener('mousedown', (e) => {
              // element with a fixedsize attribute will be ignored
              if (e.target.parentElement.getAttribute('fixedsize')) {
                  return;
              }
              resizing = true;
              document.body.addEventListener('mousemove', handleResize);
              document.body.style.cursor = 'col-resize';
              document.body.style.userSelect = 'none';

              activeTh = e.target.parentElement;
              neighbourghTh = activeTh.nextElementSibling;
              if (!neighbourghTh) {
                  neighbourghTh = activeTh.previousElementSibling;
              }
          });

          th.appendChild(bar);
      });

      document.addEventListener('mouseup', () => {
          if (!resizing) return;
          resizing = false;
          document.body.removeEventListener('mousemove', handleResize);
          document.body.style.cursor = '';
          document.body.style.userSelect = '';
          if (typeof opt.afterResize === 'function') {
              opt.afterResize(ths);
          }
      });

      // resets the column sizes
      el.$resetColumnSizes = () => {
          ths.forEach((th) => {
              th.style.width = th.originalWidth;
          }, 0);
          applyTableWidth();
      };
  }
});

// Check color scheme preferences
if ( getCookie('sgTheme') === 'dark' ) {
  console.log('Switching to darkmode');
  store.commit('setTheme', 'dark');
}

// Check timezone preferences
if ( getCookie('sgTimezone') === 'utc' ) {
  console.log('Switching to UTC timezone');
  store.commit('toggleTimezone');
}

// Check view preferences
if ( getCookie('sgView') === 'collapsed' ) {
  console.log('Switching to collapsed sidebar view');
  store.commit('toggleView');
}

// Check reload preferences
if ( getCookie('sgReload').length ) {
  console.log('Setting reload interval to ' + getCookie('sgReload'));
  store.commit('setReloadInterval', getCookie('sgReload'));
}

const vm = new Vue({
  router,
  store,
  render: h => h(App),
  data: {
    active: true,
    ip: '',
    currentCluster: '',
    currentPods: '',
    clustersData: {},
    init: false
  }
}).$mount('#app')

Vue.filter('prettyCRON', function (value) {
  return prettyCron.toString(value)
  
});

Vue.filter('formatBytes',function(a){

  // Bytes Formatter via https://stackoverflow.com/questions/15900485/correct-way-to-convert-size-in-bytes-to-kb-mb-gb-in-javascript
  if(0==a)return"0 Bytes";var c=1024,d=2,e=["Bytes","Ki","Mi","Gi","Ti","Pi","Ei","Zi","Yi"],f=Math.floor(Math.log(a)/Math.log(c));return parseFloat((a/Math.pow(c,f)).toFixed(d))+" "+e[f];

});

Vue.filter('prefix',function(s, l = 2){
  return s.substring(0, l);
});


Vue.filter('formatTimestamp',function(t, part, tzCheck = true){

  // Adjust timestamp according to user timezone
  if(!!moment().utcOffset() && tzCheck && (store.state.timezone == 'local') ) {
    t = moment.utc(t).local().format('YYYY-MM-DDTHH:mm:ss.SSS')
  }

  if(part == 'date')
    return t.substr(0, 10);
  else if (part == 'time')
    return t.substring( 11, 19);
  else if (part == 'ms') {

    if(t.includes('.')) {
      var ms = '.' + t.split('.')[1];

      for(var i = ms.length; i <= 3; i++) {
        ms += '0'
      }
      return ms.substring(0,4);
    } else {
      return '.000';
    }
  }
      
});

function getCookie(cname) {
  var name = cname + "=";
  var ca = document.cookie.split(';');
  for(var i = 0; i < ca.length; i++) {
    var c = ca[i];
    while (c.charAt(0) == ' ') {
      c = c.substring(1);
    }
    if (c.indexOf(name) == 0) {
      return c.substring(name.length, c.length);
    }
  }
  return "";
}

/* jQuery Init */

$(document).ready(function(){

  $("#namespaces li:first-of-type a").click();

  $(document).on("click", "a.namespace", function(){
    store.commit('setCurrentNamespace',$(this).text());
    $("#backup-btn, #graffana-btn").css("display","none");
  });

  $(document).on('click', '[data-tab]', function(){
    $('[data-tab].active, [data-tab="' + $(this).data('tab') + '"]').toggleClass('active');
  })

  $(document).on('click','.summary button.toggleSummary', function(){
    let el = $(this)
    let ul = el.siblings("ul")
    let parentLi = $(this).parent()

    parentLi.toggleClass("collapsed");
    ul.slideToggle();
  })

  $(document).on("click", ".box h4", function() {
    
    $(this).parents(".box").toggleClass("show");

    // Look for toggle button
    var btn = $(this).parents(".table").find(".details .btn");
    
    if(btn.length) {
      if(btn.text() == 'Details')
        btn.text(' Close ');
      else
        btn.text('Details');  
    }
  
  });

  $(document).on("click", ".details .btn", function(){

    $(this).parents(".box").toggleClass("show");

    if($(this).text() == 'Details')
      $(this).text(' Close ');
    else
      $(this).text('Details');
  
  });

  $(document).on("click", function(e) {

    if(!$(e.target).parents().addBack().is('#notifications')) {
      store.commit('toggleNotifications');
    }

    if($('#clone.show').length && !$(e.target).parents().addBack().is('.cloneCRD'))
      store.commit('setCloneCRD', {});

    if($('#restartCluster').length && !$(e.target).parents().addBack().is('.restartCluster'))
      store.commit('setRestartCluster', {namespace: '', name: ''})

  });
  
  $("#clone").click(function(e){
    e.stopPropagation();
  });

  $(document).on("click", "#nav:not(.disabled) .top a.nav-item", function(){
    $(".clu a[href$='"+store.state.currentCluster+"']").addClass("router-link-active");
  });

  $(".expand").click(function(){
    $(".set").addClass("active");
    $("#sets").addClass("expanded");
  });

  $(".collapse").click(function(){
    $(".set").removeClass("active");
    $("#sets").removeClass("expanded");
  });

  $("#nav .view").click(function(){
    $("#nav .tooltip.show").prop("class","tooltip").hide();
    $("#nav .top a.nav-item").removeClass("router-link-active");
  });

  $("#nav.disabled .top a.nav-item").click(function(){
      $("#nav .tooltip.show").prop("class","tooltip").hide();
      $(this).siblings(".tooltip").fadeIn().addClass("show");
      $("#nav .top .tooltip").addClass("pos"+($(this).index()+1));
  });

  $(".hasTooltip > a").click(function(){
    if($(this).parent().hasClass("active")) {
      $(this).parent().removeClass("active");
      $(this).parent().find("div.message").removeClass("show");
    }
    else{
      $(".hasTooltip.active").removeClass("show");
      $(this).parent().addClass("active");
      $(this).parent().find("div.message").addClass("show");
    }      
  });

  /* Disable Grafana KEY functions */
  $(".grafana iframe").contents().find("body").keyup( function(e) {
    switch (e.keyCode) {
      case 27: // 'Esc'
        event.returnValue = false;
        event.keyCode = 0;
        alert("ESC");
        break;
    }
  });


  $.fn.ulSelect = function(){
    var ul = $(this);

    if (!ul.hasClass('zg-ul-select'))
      ul.addClass('zg-ul-select');
    
    $('li:first-of-type', this).addClass('active');

    var selected = $('#selected--zg-ul-select');
      
    
    $(document).on('click', '#selected--zg-ul-select', function(){

      $(this).toggleClass('open');
      ul.toggleClass('active');

      var selectedText = $(this).text();
      if (ul.hasClass('active')) {
        selected.addClass('active');
      }
      else {
        //selected.text('').removeClass('active'); 
        $('li.active', ul);
      }
    });

    $(document).on('click', '#be-select li a', function(){
      selected.removeClass('open');
      ul.removeClass('active');
      $(".set.backups.active").removeClass('active');
    });

  }

  // Run
  $('#be-select').ulSelect();

  $("form").submit(function(e){
    e.preventDefault(); 
  });

  $(document).on("click", ".sort th span:not(.helpTooltip)", function(){
    $(".sorted").removeClass("sorted");
    $(this).addClass("sorted");
    $(".sort th").toggleClass("desc asc")   
  });

  $(document).mouseup(function(e) {
    var container = $(".filter.open");

    // if the target of the click isn't the container nor a descendant of the container
    if (!container.is(e.target) && container.has(e.target).length === 0) {
      $('.filter.open').removeClass("open");
    }
  });

  $(document).on("click",".toggle:not(.date)",function(e){
    e.stopPropagation();
    $(this).parent().toggleClass("open");
  })

  $(document).on('click', 'ul.select .selected', function(){
    $(this).parent().toggleClass('active');
  });  

  $('form.noSubmit').on('submit',function(e){
    e.preventDefault
  });

  onmousemove = function (e) {

    if( (window.innerWidth - e.clientX) > 420 ) {
      $('#nameTooltip, #infoTooltip').css({
        "top": e.clientY+20, 
        "right": "auto",
        "left": e.clientX+20
      })
    } else {
      $('#nameTooltip, #infoTooltip').css({
        "top": e.clientY+20, 
        "left": "auto",
        "right": window.innerWidth - e.clientX + 20
      })
    }
  }
  
  $(document).on('mouseenter', '.hasTooltip', function(){
    const c = $(this).children('span').first();
    $(this).append('<i class="auxTooltip">' + c.text() + '</i>');
    if($(this).hasClass('extName')) {
      if((c.width() - 40) < $(this).find('.auxTooltip').first().width()){
        $('#nameTooltip .info').text(c.text());
        $('#nameTooltip').addClass('show');
      }
    } else {
      if(c.width() < $(this).find('.auxTooltip').first().width()){
        $('#nameTooltip .info').text(c.text());
        $('#nameTooltip').addClass('show');
      }
    }
      
  });

  $(document).on('mouseleave', '.hasTooltip', function(){ 
    $(this).children('.auxTooltip').remove();
    $('#nameTooltip .info').text('');
    $('#nameTooltip').removeClass('show');
  });

  $(document).on('click mouseover','[data-tooltip]', function(e){
    if((e.type == 'click') || $(this).hasClass('onHover')) {
      let offset = $(this).offset();
      let maxHeight = window.innerHeight - (offset.top - window.scrollY) - 50

      if( (window.innerWidth - e.clientX) > 420 ) {
        $('#helpTooltip').css({ 
          "right": "auto",
          "left": e.clientX+10,
        })
      } else {
        $('#helpTooltip').css({
          "left": "auto",
          "right": window.innerWidth - e.clientX + 10,
        })
      }

      if( (window.innerHeight - e.clientY) > 240 ) {
        $('#helpTooltip').css({ 
          "bottom": "auto",
          "top": e.clientY+10,
          "max-height": maxHeight
        })
      } else {
        $('#helpTooltip').css({ 
          "top": "auto",
          "bottom": window.innerHeight - e.clientY + 10,
          "max-height": "auto"
        })
      }

      if(!$(this).hasClass('show')) {
        store.commit('setTooltipsText', $(this).data('tooltip'))
        $('.helpTooltip.show').removeClass('show')
        $('#helpTooltip').addClass('show').show()
      } else {
        store.commit('setTooltipsText','Click on a question mark to get help and tips about that field.')
        $('#helpTooltip').removeClass('show').hide()
      }

      $(this).toggleClass('show')  
    }
  });

  $(document).on('mouseleave', '[data-tooltip]', function(e) {
    if(!e.target.classList.contains('helpTooltip') || e.target.classList.contains('onHover')) {
      store.commit('setTooltipsText','Click on a question mark to get help and tips about that field.')
      $('#helpTooltip').removeClass('show').hide()
      $(e.target).removeClass('show')
    }
  });
  
  $(document).on("click", "#helpTooltip a", function(e) {
    e.preventDefault()
    window.open($(this).prop('href'));
    return false;
  })

  $(document).on('click','a.help', function(){
    $('a.help.active').removeClass('active')
    $(this).addClass('active')
  })

  $(document).on('click', '.contentTooltip .close', function(){
    $(this).parents('.contentTooltip').remove()
  })

  $(document).on("click", "#side", function(e) {

    if($('.contentTooltip').hasClass('show')) {
      $('.contentTooltip').removeClass('show')
      $('.contentTooltip .content').html('');
    }
  });

  // Hide divs on click out
  $(document).click(function(event) { 
    var $target = $(event.target);
    
    if( $('.hideOnClick.show').length && !$target.closest('.show').length) {
      $('.hideOnClick.show').removeClass('show').fadeOut()
      $('.helpTooltip.show, [data-tooltip].show').removeClass('show')
    }

    if(!$target.parents('ul.select').length && $('ul.select').hasClass('active')) {
      $('ul.select.active').removeClass('active')
    }
    
  });

  $(window).on('scroll', function(){
    $('#helpTooltip.show').removeClass('show').fadeOut()
    $('.helpTooltip.show').removeClass('show')
  })

  // Remove notValid class from changed fields
  function removeNotvalid(dataFieldset) {
    var event = new CustomEvent('fieldSetListener', {
      detail: {
        fieldset: dataFieldset
      }
    })

    window.dispatchEvent(event)
  };

  $(document).on('change keyup','.notValid', function(){
    if( (($(this).val() != '') && ($(this).val() != null)) || ($(this).is('label') && $(this).find('input[type="checkbox"]').is(':checked'))  ) {
      let field = $(this).data('field');
      $('[data-field="' + field + '"]').removeClass('notValid');

      if($(this).is(':radio') && $(this).parents('.optionBoxes')) {
        $(this).parents('.optionBoxes').find('[required]').removeAttr('required')
      }

      if($(this).parent('.timeSelect'))
        $(this).siblings('select').removeClass('notValid');

      if($(this).parent('label'))
        $(this).parent('label').removeClass('notValid');
      
      let fieldset = $(this).parents('fieldset[data-fieldset]').last();
      let notValidFields = fieldset.find('.notValid')
      
      if(!notValidFields.length) {
        let dataFieldset = fieldset.attr('data-fieldset');

        removeNotvalid(dataFieldset)
      }
    }
  });
  
  $(document).on('click','.copyClipboard', function(){
    let el = $(this)
    let copyText = document.getElementById('copyText');
    copyText.value = el.parent().text();
    copyText.select();
    copyText.setSelectionRange(0, 99999); // For mobile devices
    document.execCommand("copy");
    setTimeout(function(){
      store.commit('setTooltipsText','Click on a question mark to get help and tips about that field.')
      $('#helpTooltip').removeClass('show').hide()
    },3000)
  })

});
